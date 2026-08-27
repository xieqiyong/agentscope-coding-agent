package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.SkillEntity;
import com.agentplatform.persistence.repository.SkillRepository;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 定义管理服务。
 * 提供技能的增删改查、Agent Skills 标准 zip 包导入，以及聊天框 @ 动态挂载时的按名加载。
 */
@Service
public class SkillService {

    /**
     * Agent Skills 标准规定的技能名称规则：小写字母/数字/连字符，不以连字符开头结尾，无连续连字符，长度 1-64。
     */
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Resource
    private SkillRepository skillRepository;

    /**
     * 导入技能解压后的存储目录，可通过 agent.skill.store-dir 配置。
     */
    @Value("${agent.skill.store-dir:data/skills}")
    private String storeDir;

    /**
     * 查询技能列表，enabledOnly 为 true 时只返回启用中的技能。
     */
    public List<SkillEntity> list(boolean enabledOnly) {
        return enabledOnly
                ? skillRepository.findByEnabledTrueOrderByNameAsc()
                : skillRepository.findAll().stream()
                        .sorted((a, b) -> safe(a.getName()).compareToIgnoreCase(safe(b.getName())))
                        .toList();
    }

    /**
     * 按名称集合加载启用中的技能，用于运行时动态挂载。
     */
    public List<SkillEntity> loadEnabledByName(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = names.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return skillRepository.findByNameInAndEnabledTrue(cleaned);
    }

    /**
     * 新建或更新技能；id 为空时新建，同名技能会直接报冲突。
     */
    public SkillEntity save(Long id, String name, String description, String content, Boolean enabled) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "技能名称不能为空");
        }
        SkillEntity entity;
        if (id != null) {
            entity = skillRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(404, "技能不存在"));
        } else {
            entity = new SkillEntity();
            if (skillRepository.findByName(name.trim()).isPresent()) {
                throw new BusinessException(409, "同名技能已存在");
            }
        }
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setContent(content);
        entity.setEnabled(enabled == null || enabled);
        // 页面手工创建/编辑的技能来源标记为 LOCAL；导入技能的 IMPORTED 标记不受编辑影响
        if (!StringUtils.hasText(entity.getSource())) {
            entity.setSource("LOCAL");
        }
        return skillRepository.save(entity);
    }

    /**
     * 删除技能。
     */
    public void delete(Long id) {
        if (id == null || !skillRepository.existsById(id)) {
            throw new BusinessException(404, "技能不存在");
        }
        skillRepository.deleteById(id);
    }

    /**
     * 导入 Agent Skills 标准 zip 包。
     * zip 内应是技能目录（SKILL.md 在根或唯一顶层目录下），可附带 scripts/references/assets 等文件。
     */
    public SkillEntity importZip(byte[] zipBytes, String fileName) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(400, "请上传技能 zip 包");
        }
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new BusinessException(400, "只支持 .zip 格式的技能包");
        }
        List<ZipItem> items = readZip(zipBytes);
        if (items.isEmpty()) {
            throw new BusinessException(400, "zip 包内容为空");
        }

        // 定位 SKILL.md：根目录或唯一顶层目录下
        String skillMdPath = locateSkillMd(items);
        if (skillMdPath == null) {
            throw new BusinessException(400, "zip 包中找不到 SKILL.md（应位于技能目录根或唯一顶层目录）");
        }
        String prefix = skillMdPath.contains("/") ? skillMdPath.substring(0, skillMdPath.indexOf('/') + 1) : "";

        String skillMd = readText(items, skillMdPath);
        Frontmatter fm = parseFrontmatter(skillMd);
        if (!StringUtils.hasText(fm.name)) {
            throw new BusinessException(400, "SKILL.md 缺少必填的 name 字段");
        }
        if (fm.name.length() > 64 || !SKILL_NAME_PATTERN.matcher(fm.name).matches()) {
            throw new BusinessException(400, "技能名称不合规：仅允许小写字母、数字和连字符，不以连字符开头结尾，最长 64 字符");
        }
        if (!StringUtils.hasText(fm.description)) {
            throw new BusinessException(400, "SKILL.md 缺少必填的 description 字段");
        }
        if (skillRepository.findByName(fm.name).isPresent()) {
            throw new BusinessException(409, "同名技能已存在：" + fm.name);
        }

        // 解压到平台技能存储目录；content 存正文 + 打包文件清单
        List<String> bundleFiles = new ArrayList<>();
        try {
            Path targetDir = Paths.get(storeDir).toAbsolutePath().normalize().resolve(fm.name);
            for (ZipItem item : items) {
                String relative = prefix.isEmpty() ? item.name : item.name.substring(prefix.length());
                if (relative.isBlank() || relative.endsWith("/")) {
                    continue;
                }
                Path dest = safeResolve(targetDir, relative);
                Files.createDirectories(dest.getParent());
                Files.copy(new ByteArrayInputStream(item.data), dest, StandardCopyOption.REPLACE_EXISTING);
                bundleFiles.add(relative);
            }
            SkillEntity entity = new SkillEntity();
            entity.setName(fm.name);
            entity.setDescription(fm.description);
            entity.setContent(buildImportedContent(fm.body, bundleFiles));
            entity.setEnabled(true);
            entity.setSource("IMPORTED");
            entity.setBundlePath(Paths.get(storeDir).toAbsolutePath().normalize().resolve(fm.name).toString());
            return skillRepository.save(entity);
        } catch (IOException e) {
            throw new BusinessException(500, "解压技能包失败：" + e.getMessage());
        }
    }

    /**
     * 读取 zip 全部条目到内存（技能包通常很小），并做路径安全校验防止 zip-slip。
     */
    private List<ZipItem> readZip(byte[] zipBytes) {
        List<ZipItem> items = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (name == null || name.isBlank() || name.endsWith("/")) {
                    continue;
                }
                // 忽略 macOS 打包产生的 __MACOSX 目录和隐藏文件/目录（.DS_Store、.git 等）
                if (name.startsWith("__MACOSX/") || isHiddenSegment(name)) {
                    continue;
                }
                items.add(new ZipItem(name, zis.readAllBytes()));
            }
        } catch (IOException e) {
            throw new BusinessException(400, "读取 zip 包失败：" + e.getMessage());
        }
        return items;
    }

    /**
     * 归一化 zip 条目名并拒绝路径穿越。
     */
    private String normalizeZipEntryName(String raw) {
        String name = raw.replace('\\', '/');
        Path normalized = Paths.get(name).normalize();
        if (normalized.isAbsolute() || name.startsWith("..") || name.contains("/../") || name.equals("..")) {
            return null;
        }
        return normalized.toString().replace('\\', '/');
    }

    private boolean isHiddenSegment(String name) {
        String[] segments = name.split("/");
        for (String segment : segments) {
            if (segment.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 定位 SKILL.md：优先根目录，其次是唯一顶层目录下。
     */
    private String locateSkillMd(List<ZipItem> items) {
        String rootHit = null;
        String nestedHit = null;
        for (ZipItem item : items) {
            if ("SKILL.md".equals(item.name)) {
                rootHit = item.name;
            } else if (item.name.endsWith("/SKILL.md")) {
                if (nestedHit == null || item.name.length() < nestedHit.length()) {
                    nestedHit = item.name;
                }
            }
        }
        if (rootHit != null) {
            return rootHit;
        }
        if (nestedHit != null) {
            // 确认 SKILL.md 的顶层目录是唯一顶层目录
            String topDir = nestedHit.substring(0, nestedHit.indexOf('/') + 1);
            boolean allUnderTop = items.stream().allMatch(item -> item.name.startsWith(topDir));
            if (allUnderTop) {
                return nestedHit;
            }
        }
        return null;
    }

    private String readText(List<ZipItem> items, String path) {
        for (ZipItem item : items) {
            if (item.name.equals(path)) {
                return new String(item.data, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * 解析 SKILL.md 的 YAML frontmatter。
     * 中文注释：只提取平铺的标量字段（name/description/license/compatibility），嵌套结构按标准为可选，这里不解析。
     */
    private Frontmatter parseFrontmatter(String skillMd) {
        Frontmatter fm = new Frontmatter();
        String text = skillMd.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new BusinessException(400, "SKILL.md 必须以 YAML frontmatter 开头（--- 包围）");
        }
        int end = text.indexOf("\n---", 4);
        if (end < 0) {
            throw new BusinessException(400, "SKILL.md frontmatter 未闭合");
        }
        String frontmatter = text.substring(4, end);
        String body = text.substring(Math.min(text.length(), end + 4)).replaceFirst("^\\n", "");
        for (String line : frontmatter.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0 || line.startsWith(" ") || line.startsWith("#")) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if ("name".equals(key)) {
                fm.name = value;
            } else if ("description".equals(key)) {
                fm.description = value;
            }
        }
        fm.body = body;
        return fm;
    }

    /**
     * 组装导入技能的提示词内容：SKILL.md 正文 + 打包文件清单说明。
     */
    private String buildImportedContent(String body, List<String> bundleFiles) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(body)) {
            builder.append(body.trim());
        }
        if (!bundleFiles.isEmpty()) {
            builder.append("\n\n【技能打包文件】\n");
            for (String file : bundleFiles) {
                if (!"SKILL.md".equals(file)) {
                    builder.append("- ").append(file).append('\n');
                }
            }
            builder.append("以上文件已随技能导入平台；其中脚本类文件当前仅作为参考内容，平台尚未开放直接执行，不要假装已经运行过。");
        }
        return builder.toString();
    }

    /**
     * 在目标目录内安全解析相对路径，防止越界写入。
     */
    private Path safeResolve(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException(400, "技能包内存在非法路径：" + relative);
        }
        return resolved;
    }

    /**
     * zip 内的单个文件条目。
     */
    private record ZipItem(String name, byte[] data) {
    }

    /**
     * SKILL.md 解析结果。
     */
    private static class Frontmatter {
        String name;
        String description;
        String body;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
