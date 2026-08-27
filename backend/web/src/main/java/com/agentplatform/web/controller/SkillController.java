package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.SkillEntity;
import com.agentplatform.runtime.service.SkillService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Skill 管理接口。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Resource
    private SkillService skillService;

    @PostMapping("/list")
    public ApiResponse<List<SkillEntity>> list(@RequestBody(required = false) Map<String, Object> body) {
        boolean enabledOnly = body != null && Boolean.TRUE.equals(body.get("enabledOnly"));
        return ApiResponse.success(skillService.list(enabledOnly));
    }

    @PostMapping("/save")
    public ApiResponse<SkillEntity> save(@RequestBody Map<String, Object> body) {
        SkillEntity entity = skillService.save(
                parseLong(body.get("id")),
                text(body.get("name")),
                text(body.get("description")),
                text(body.get("content")),
                body.get("enabled") == null ? null : Boolean.parseBoolean(String.valueOf(body.get("enabled")))
        );
        return ApiResponse.success(entity);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody Map<String, Object> body) {
        skillService.delete(parseLong(body.get("id")));
        return ApiResponse.success(null);
    }

    /**
     * 导入 Agent Skills 标准 zip 包（SKILL.md + 可选 scripts/references/assets）。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillEntity> importZip(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        return ApiResponse.success(skillService.importZip(file.getBytes(), file.getOriginalFilename()));
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
