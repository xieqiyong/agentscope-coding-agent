package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.ConversationEntity;
import com.agentplatform.persistence.repository.ConversationMessageRepository;
import com.agentplatform.persistence.repository.ConversationRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理接口。
 * MVP 阶段直接操作 Repository，后续可迁移到 ConversationApplicationService。
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ConversationMessageRepository conversationMessageRepository;

    /**
     * 查询工作区下的会话列表。
     */
    @GetMapping
    public ApiResponse<List<ConversationEntity>> list(@RequestParam Long workspaceId) {
        List<ConversationEntity> sessions = conversationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return ApiResponse.success(sessions);
    }

    /**
     * 获取单个会话详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ConversationEntity> getById(@PathVariable Long id) {
        return conversationRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /**
     * 创建新会话。
     */
    @PostMapping
    public ApiResponse<ConversationEntity> create(@RequestBody Map<String, Object> body) {
        ConversationEntity entity = new ConversationEntity();
        entity.setTitle((String) body.getOrDefault("title", "新会话"));
        entity.setStatus("ACTIVE");

        if (body.get("workspaceId") != null) {
            entity.setWorkspaceId(toLong(body.get("workspaceId")));
        }
        if (body.get("agentId") != null) {
            entity.setAgentId(toLong(body.get("agentId")));
        }

        ConversationEntity saved = conversationRepository.save(entity);
        return ApiResponse.success(saved);
    }

    /**
     * 删除会话。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationRepository.deleteById(id);
        return ApiResponse.success(null);
    }

    /**
     * 查询会话消息列表。
     */
    @GetMapping("/{id}/messages")
    public ApiResponse<?> listMessages(@PathVariable Long id) {
        return ApiResponse.success(conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(id));
    }

    private Long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return null;
    }
}
