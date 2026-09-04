package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.memory.service.MemoryContextAssembler;
import com.agentplatform.persistence.entity.AgentEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.entity.MemoryEntryEntity;
import com.agentplatform.persistence.entity.ModelConfigEntity;
import com.agentplatform.persistence.entity.WorkspaceEntity;
import com.agentplatform.persistence.repository.AgentRepository;
import com.agentplatform.persistence.repository.ConversationMessageRepository;
import com.agentplatform.persistence.repository.ModelConfigRepository;
import com.agentplatform.persistence.repository.WorkspaceRepository;
import com.agentplatform.runtime.model.AgentRunCommand;
import com.agentplatform.runtime.model.RuntimeContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 智能体运行上下文构建器。
 * 它只负责把数据库里的稳定事实组装成一次运行快照，不直接调用模型。
 */
@Service
public class AgentRunContextBuilder {

    @Resource
    private WorkspaceRepository workspaceRepository;

    @Resource
    private AgentRepository agentRepository;

    @Resource
    private ModelConfigRepository modelConfigRepository;

    @Resource
    private ConversationMessageRepository conversationMessageRepository;

    @Resource
    private com.agentplatform.persistence.repository.SkillRepository skillRepository;

    @Resource
    private MemoryContextAssembler memoryContextAssembler;

    public RuntimeContext build(AgentRunCommand command, Long conversationId, Long userMessageId,
                                Long runId, String traceId) {
        WorkspaceEntity workspace = workspaceRepository.findById(command.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(404, "工作区不存在"));
        AgentEntity agent = agentRepository.findById(command.getAgentId())
                .orElseThrow(() -> new BusinessException(404, "智能体不存在"));

        ModelConfigEntity modelConfig = resolveModelConfig(agent);

        String userId = StringUtils.hasText(command.getUserId()) ? command.getUserId() : "default";
        List<MemoryEntryEntity> activeMemories = memoryContextAssembler.loadActiveMemories(workspace.getId(), userId);

        List<ConversationMessageEntity> recentMessages = limitRecentMessages(
                conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId));

        RuntimeContext context = new RuntimeContext();
        context.setCommand(command);
        context.setWorkspace(workspace);
        context.setAgent(agent);
        context.setModelConfig(modelConfig);
        context.setConversationId(conversationId);
        context.setUserMessageId(userMessageId);
        context.setRunId(runId);
        context.setTraceId(traceId);
        context.setRecentMessages(recentMessages);
        context.setActiveMemories(activeMemories);
        // 模型优先级：Agent 绑定的模型（resolveModelConfig 已含默认兜底）优先，命令参数退为 fallback。
        // 这样不同 Agent 可以绑定不同模型；Agent 没绑定时 resolveModelConfig 返回默认配置，行为与原 command 优先一致。
        String resolvedBaseUrl = modelConfig != null ? modelConfig.getBaseUrl() : null;
        String resolvedModelName = modelConfig != null ? modelConfig.getModelName() : null;
        String resolvedApiKey = modelConfig != null ? modelConfig.getApiKeyCipher() : null;
        context.setModelBaseUrl(firstNonBlank(resolvedBaseUrl, command.getModelBaseUrl()));
        context.setModelName(firstNonBlank(resolvedModelName, command.getModelName()));
        // 第一版暂时直接使用命令中的 key 或配置密文字段；后续应接入真正的密钥解密服务。
        context.setApiKey(firstNonBlank(resolvedApiKey, command.getApiKey()));
        context.setMaxIterations(firstPositive(command.getMaxIterations(), agent.getMaxIterations(), 8));
        context.setTimeoutSeconds(firstPositive(command.getTimeoutSeconds(), agent.getTimeoutSeconds(), 86400));
        // 聊天框 @ 动态挂载的技能：只对本次运行生效，附加在 Agent 系统提示词之后。
        context.setSystemPrompt(buildSystemPrompt(agent, workspace, activeMemories)
                + buildMountedSkillsPrompt(command.getMountedSkills()));
        return context;
    }

    /**
     * 将当前运行上下文临时切换到指定 Agent。
     * 中文注释：多 Agent 编排按步骤切换专家时使用，调用方负责在步骤结束后恢复原上下文。
     */
    public boolean applyAgentOverride(RuntimeContext context, Long agentId) {
        if (context == null || agentId == null) {
            return false;
        }
        AgentEntity agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null || !"ENABLED".equalsIgnoreCase(agent.getStatus())) {
            return false;
        }
        ModelConfigEntity modelConfig = resolveModelConfig(agent);
        context.setAgent(agent);
        context.setModelConfig(modelConfig);
        if (modelConfig != null) {
            context.setModelBaseUrl(modelConfig.getBaseUrl());
            context.setModelName(modelConfig.getModelName());
            context.setApiKey(modelConfig.getApiKeyCipher());
        }
        context.setMaxIterations(firstPositive(null, agent.getMaxIterations(), context.getMaxIterations()));
        context.setTimeoutSeconds(firstPositive(context.getCommand().getTimeoutSeconds(),
                agent.getTimeoutSeconds(), firstPositive(null, context.getTimeoutSeconds(), 86400)));
        context.setSystemPrompt(buildSystemPrompt(agent, context.getWorkspace(), context.getActiveMemories()));
        return true;
    }

    /**
     * 中文注释：历史窗口截断——只把最近一段对话送进模型，避免会话变长后每轮全量重发导致 token 消耗失控。
     * 默认保留最近 20 条且总字符不超过 24000，超出部分从最旧开始丢弃。
     */
    private List<ConversationMessageEntity> limitRecentMessages(List<ConversationMessageEntity> messages) {
        if (messages == null || messages.size() <= 20) {
            return messages == null ? List.of() : messages;
        }
        int budgetChars = 24000;
        int total = 0;
        int start = messages.size();
        for (int i = messages.size() - 1; i >= 0 && (messages.size() - i) <= 20; i--) {
            int len = messages.get(i).getContent() == null ? 0 : messages.get(i).getContent().length();
            if (total + len > budgetChars) {
                break;
            }
            total += len;
            start = i;
        }
        if (start == 0) {
            return messages;
        }
        return messages.subList(start, messages.size());
    }

    private String buildSystemPrompt(AgentEntity agent, WorkspaceEntity workspace, List<MemoryEntryEntity> memories) {
        String basePrompt = StringUtils.hasText(agent.getSystemPrompt())
                ? agent.getSystemPrompt()
                : "你是一个严谨的通用编码智能体。先判断用户请求是否依赖当前工作区；依赖项目事实时要查证据，不依赖项目事实时可以直接回答。";

        basePrompt = basePrompt + buildCurrentTimePrompt();

        basePrompt = basePrompt + "\n\n身份边界：\n"
                + "1. 当前智能体身份只来自 Agent 名称和系统提示词，不来自工作区名称、目录名或项目记忆。\n"
                + "2. 工作区名称只是当前任务上下文；除非用户明确要求介绍项目或工作区，不要把工作区名写进自我介绍。\n"
                + "3. 用户问“你是谁”“介绍一下你自己”时，只介绍当前智能体，不要自称内部路由节点，也不要代入项目名。";

        basePrompt = basePrompt + "\n\n工具选择策略：\n"
                + "1. 用户问编程概念、面试题、通用设计、学习路线、聊天确认等不依赖当前工作区的问题时，直接回答，不要为了使用工具而调用工具。\n"
                + "2. 用户问“这个项目、当前代码、某个文件、某个接口、为什么报错、帮我修改”等依赖工作区事实的问题时，必须先用工具获取证据。\n"
                + "3. 用户明确说“不用读代码、直接说思路、先给方案”时，除非缺少关键信息，否则先直接回答。\n"
                + "4. 涉及最新外部资料、版本、新闻、价格、政策时使用 WebSearch，并说明信息来自搜索结果。";

        basePrompt = basePrompt + "\n\n证据充分性规则：\n"
                + "1. 对项目代码下结论前，至少说明结论依据来自哪些已读取文件或搜索结果；如果只看了部分文件，要明确这是基于当前证据的判断。\n"
                + "2. 修改代码前，必须读取目标文件；涉及调用链、配置、测试或前后端联动时，还要读取相关调用方、配置文件或测试文件。\n"
                + "3. 不要只凭文件名、目录名或训练先验判断实现细节；工具结果优先于猜测。\n"
                + "4. 证据不足时不要强行下结论，应继续搜索/读取，或者明确告诉用户还缺哪些信息。";

        basePrompt = basePrompt + "\n\nClaude Code 风格工具补充：\n"
                + "1. 探索文件优先使用 LS、Glob、Grep、Read。\n"
                + "2. 当前 workspace 内的普通代码修改，优先使用 apply_patch 或 Edit 直接应用最小修改。\n"
                + "3. 新建文件、整文件替换或大文件修改时，使用 Write。\n"
                + "4. 多文件任务必须拆成多个工具调用，不要让用户手动复制粘贴文件内容。\n"
                + "5. propose_patch 和 propose_file_change 只保存修改提案，不会直接写入磁盘；需要只生成方案时才使用。\n"
                + "6. 只有 Write、Edit 或 apply_patch 返回成功后，才能声称文件已经创建或修改完成。\n"
                + "7. 工具权限审批已关闭；文件工具只校验路径必须在当前 workspace 内，Bash 只校验工作目录必须在当前 workspace 内。\n"
                + "8. Bash 已开放，可用于当前 workspace 内的构建、测试、代码生成和排障命令；命令输出会被平台截断并受超时限制。\n"
                + "9. 不要在回答正文中输出 DSML、XML、tool_calls、invoke、parameter 等工具调用协议标签；需要工具时必须调用平台工具。\n"
                + "10. Notebook、子 Agent、任务编排暂未开放，不要假装已经执行这些工具。";

        basePrompt = basePrompt + buildAgentBindingsPrompt(agent);

        String memoryText = memoryContextAssembler.assemblePromptSection(memories);

        return """
                %s

                【工作区】
                - 名称：%s
                - 根目录：%s

                【已知偏好与项目约束】
                %s
                【运行规则】
                1. 不要臆测工作区文件内容；需要项目事实时必须通过工具读取。
                2. 所有文件路径都必须视为相对工作区路径。
                3. 修改代码时优先做最小改动，并在回答里说明实际修改了哪些文件。
                4. 不要在最终回答中主动输出密钥、token、证书、私钥等敏感内容；确需处理时只说明文件和字段位置。
                5. 工具结果优先于模型猜测；如果证据不足，要明确说明。
                6. 上方偏好和约束是隐式协作规则，按规则行动即可；不要在回答中主动解释这些规则来自哪里，也不要主动说已经保存或记录，除非用户明确询问已知偏好、项目约束或要求确认保存结果。
                """.formatted(basePrompt, workspace.getName(), workspace.getRootPath(), memoryText);
    }

    /**
     * 组装本次运行动态挂载的 Skills 提示词段落。
     * 中文注释：找不到或已停用的技能会被静默跳过，不阻断运行。
     */
    private String buildMountedSkillsPrompt(List<String> mountedSkills) {
        if (mountedSkills == null || mountedSkills.isEmpty()) {
            return "";
        }
        List<com.agentplatform.persistence.entity.SkillEntity> skills =
                skillRepository.findByNameInAndEnabledTrue(mountedSkills);
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("\n\n【本次动态挂载的 Skills】");
        for (com.agentplatform.persistence.entity.SkillEntity skill : skills) {
            builder.append("\n### ").append(skill.getName());
            if (StringUtils.hasText(skill.getDescription())) {
                builder.append(" - ").append(skill.getDescription());
            }
            if (StringUtils.hasText(skill.getContent())) {
                builder.append('\n').append(skill.getContent());
            }
        }
        builder.append("\n以上 Skills 是用户在本次对话中显式挂载的操作指引，按其步骤执行；与项目事实冲突时以工具读取的证据为准。");
        return builder.toString();
    }

    private String buildAgentBindingsPrompt(AgentEntity agent) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(agent.getSkillsJson())) {
            builder.append("\n\n【当前 Agent 绑定的 Skills 配置】\n")
                    .append(agent.getSkillsJson())
                    .append("\n这些 Skills 是平台侧配置说明。只有已经注册到平台工具层的能力才能实际执行，文件与命令仍受 workspace 边界约束。");
        }
        if (StringUtils.hasText(agent.getMcpServicesJson())) {
            builder.append("\n\n【当前 Agent 绑定的 MCP 服务配置】\n")
                    .append(agent.getMcpServicesJson())
                    .append("\n不要声称已经调用未接入的平台 MCP 服务；需要外部服务时先说明当前只完成绑定配置。");
        }
        return builder.toString();
    }

    private String buildCurrentTimePrompt() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String dateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        return "\n\n【当前时间】\n"
                + "- 当前日期：" + now.toLocalDate() + "\n"
                + "- 当前时间：" + dateTime + "\n"
                + "- 当前时区：Asia/Shanghai\n"
                + "- 用户提到今天、昨天、明天、今年、最新、最近时，必须以这里的日期为准。\n"
                + "- 不要根据训练数据猜日期；涉及最新外部事实、版本、新闻、价格、政策时，必须使用 WebSearch 或明确说明未联网验证。";
    }

    private ModelConfigEntity resolveModelConfig(AgentEntity agent) {
        if (agent.getModelConfigId() != null) {
            ModelConfigEntity boundConfig = modelConfigRepository.findById(agent.getModelConfigId()).orElse(null);
            if (boundConfig != null) {
                return boundConfig;
            }
        }
        // 中文注释：审批恢复、计划执行等二次请求可能不会带模型参数，统一兜底到默认模型配置。
        return modelConfigRepository.findByDefaultConfigTrue().stream().findFirst().orElse(null);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private int firstPositive(Integer first, Integer second, int fallback) {
        if (first != null && first > 0) {
            return first;
        }
        if (second != null && second > 0) {
            return second;
        }
        return fallback;
    }
}
