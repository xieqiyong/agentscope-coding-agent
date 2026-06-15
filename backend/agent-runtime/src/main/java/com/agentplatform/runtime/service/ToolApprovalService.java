package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.AgentRunEntity;
import com.agentplatform.persistence.entity.ApprovalRequestEntity;
import com.agentplatform.persistence.enums.AgentRunStatus;
import com.agentplatform.persistence.enums.ApprovalStatus;
import com.agentplatform.persistence.repository.AgentRunRepository;
import com.agentplatform.persistence.repository.ApprovalRequestRepository;
import com.agentplatform.runtime.model.AgentApprovalCommand;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolUseBlock;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具审批服务。
 * 中文注释：AgentScope 负责产生“需要确认”的事件；这里把事件转换成平台审批记录，并在用户决策后重建恢复参数。
 */
@Service
public class ToolApprovalService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Resource
    private ApprovalRequestRepository approvalRequestRepository;

    @Resource
    private AgentRunRepository agentRunRepository;

    @Resource
    private ObjectMapper objectMapper;

    public List<Map<String, Object>> createPendingApprovals(RuntimeEvent event) {
        if (event == null || event.getRunId() == null || event.getMetadata() == null) {
            return List.of();
        }

        AgentRunEntity run = agentRunRepository.findById(event.getRunId())
                .orElseThrow(() -> new BusinessException(404, "Agent Run 不存在：" + event.getRunId()));
        List<Map<String, Object>> toolCalls = readToolCalls(event.getMetadata().get("toolCalls"));
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        String replyId = stringValue(event.getMetadata().get("replyId"));
        List<Map<String, Object>> approvals = new ArrayList<>();
        for (Map<String, Object> toolCall : toolCalls) {
            ApprovalRequestEntity entity = new ApprovalRequestEntity();
            entity.setRunId(run.getId());
            entity.setWorkspaceId(run.getWorkspaceId());
            entity.setRequestType("TOOL_PERMISSION");
            entity.setTitle("确认执行工具 " + stringValue(toolCall.get("name")));
            entity.setDetailJson(toJson(buildDetail(replyId, toolCall)));
            entity.setStatus(ApprovalStatus.PENDING.name());
            entity = approvalRequestRepository.save(entity);

            Map<String, Object> approval = new LinkedHashMap<>();
            approval.put("approvalId", entity.getId());
            approval.put("toolCallId", stringValue(toolCall.get("id")));
            approval.put("toolName", stringValue(toolCall.get("name")));
            approval.put("riskLevel", classifyRisk(stringValue(toolCall.get("name"))));
            approval.put("status", entity.getStatus());
            approvals.add(approval);
        }
        return approvals;
    }

    public ApprovalRequestEntity decide(AgentApprovalCommand command) {
        if (command == null || command.getApprovalRequestId() == null) {
            throw new BusinessException(400, "审批请求 ID 不能为空");
        }
        ApprovalRequestEntity approval = approvalRequestRepository.findById(command.getApprovalRequestId())
                .orElseThrow(() -> new BusinessException(404, "审批请求不存在：" + command.getApprovalRequestId()));
        if (command.getRunId() != null && !command.getRunId().equals(approval.getRunId())) {
            throw new BusinessException(400, "审批请求与 Agent Run 不匹配");
        }
        if (!ApprovalStatus.PENDING.name().equals(approval.getStatus())) {
            throw new BusinessException(409, "审批请求已经处理：" + approval.getStatus());
        }
        AgentRunEntity run = agentRunRepository.findById(approval.getRunId())
                .orElseThrow(() -> new BusinessException(404, "Agent Run 不存在：" + approval.getRunId()));
        if (AgentRunStatus.from(run.getStatus()).isTerminal()) {
            throw new BusinessException(409, "Agent Run 已经结束，不能继续审批恢复：" + run.getStatus());
        }

        approval.setStatus(Boolean.TRUE.equals(command.getApproved())
                ? ApprovalStatus.APPROVED.name()
                : ApprovalStatus.REJECTED.name());
        approval.setDecidedBy(StringUtils.hasText(command.getUserId()) ? command.getUserId() : "default");
        approval.setDecidedAt(LocalDateTime.now());
        return approvalRequestRepository.save(approval);
    }

    public ToolApprovalPayload loadPayload(ApprovalRequestEntity approval) {
        if (approval == null || !StringUtils.hasText(approval.getDetailJson())) {
            throw new BusinessException(400, "审批详情为空，无法恢复工具调用");
        }
        Map<String, Object> detail = fromJson(approval.getDetailJson());
        Map<String, Object> toolCall = readMap(detail.get("toolCall"));
        if (toolCall.isEmpty()) {
            throw new BusinessException(400, "审批详情缺少工具调用信息");
        }

        String toolCallId = stringValue(toolCall.get("id"));
        String toolName = stringValue(toolCall.get("name"));
        if (!StringUtils.hasText(toolCallId) || !StringUtils.hasText(toolName)) {
            throw new BusinessException(400, "审批详情中的工具调用 ID 或名称为空");
        }

        ToolUseBlock block = ToolUseBlock.builder()
                .id(toolCallId)
                .name(toolName)
                .input(readMap(toolCall.get("input")))
                .content(stringValue(toolCall.get("content")))
                .metadata(readMap(toolCall.get("metadata")))
                .build();
        return new ToolApprovalPayload(stringValue(detail.get("replyId")), block);
    }

    private Map<String, Object> buildDetail(String replyId, Map<String, Object> toolCall) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("replyId", replyId);
        detail.put("requestType", "TOOL_PERMISSION");
        detail.put("riskLevel", classifyRisk(stringValue(toolCall.get("name"))));
        detail.put("reason", "AgentScope PermissionEngine 要求用户确认后再执行工具");
        detail.put("toolCall", toolCall);
        return detail;
    }

    private List<Map<String, Object>> readToolCalls(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> map = readMap(item);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private String classifyRisk(String toolName) {
        if (isCommandTool(toolName)) {
            return "CRITICAL";
        }
        if ("apply_patch".equals(toolName) || "Edit".equals(toolName)) {
            return "HIGH";
        }
        if ("write_file".equals(toolName) || "Write".equals(toolName)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private boolean isCommandTool(String toolName) {
        return "Bash".equalsIgnoreCase(toolName)
                || "Shell".equalsIgnoreCase(toolName)
                || "run_command".equalsIgnoreCase(toolName)
                || "runCommand".equalsIgnoreCase(toolName);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "审批详情序列化失败：" + e.getMessage());
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(500, "审批详情解析失败：" + e.getMessage());
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record ToolApprovalPayload(String replyId, ToolUseBlock toolCall) {
    }
}
