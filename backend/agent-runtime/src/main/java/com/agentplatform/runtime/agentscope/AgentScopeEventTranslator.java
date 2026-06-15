package com.agentplatform.runtime.agentscope;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventType;
import io.agentscope.core.event.*;
import io.agentscope.core.message.ToolUseBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentScope 事件翻译器。
 * 这里把 AgentScope 原始事件翻译成平台事件，并提供中文 stage，方便前端展示和学习调试。
 */
class AgentScopeEventTranslator {

    List<RuntimeEvent> translate(AgentEvent event, RuntimeContext context, long elapsedMs, boolean exposeThinking) {
        List<RuntimeEvent> events = new ArrayList<>();

        if (event instanceof AgentStartEvent e) {
            events.add(of(context, RuntimeEventType.AGENT_STARTED, "智能体已启动", null,
                    Map.of("agentName", safe(e.getName()), "role", safe(e.getRole())), elapsedMs));
        } else if (event instanceof AgentEndEvent) {
            events.add(of(context, RuntimeEventType.AGENT_FINISHED, "智能体已结束", null, Map.of(), elapsedMs));
        } else if (event instanceof ModelCallStartEvent) {
            events.add(of(context, RuntimeEventType.MODEL_CALL_STARTED, "开始调用大模型", null, Map.of(), elapsedMs));
        } else if (event instanceof ModelCallEndEvent e) {
            events.add(of(context, RuntimeEventType.MODEL_CALL_FINISHED, "大模型调用完成", null,
                    Map.of(
                            "inputTokens", e.getUsage() != null ? e.getUsage().getInputTokens() : 0,
                            "outputTokens", e.getUsage() != null ? e.getUsage().getOutputTokens() : 0,
                            "totalTokens", e.getUsage() != null ? e.getUsage().getTotalTokens() : 0,
                            "modelElapsedSeconds", e.getUsage() != null ? e.getUsage().getTime() : 0
                    ), elapsedMs));
        } else if (event instanceof TextBlockStartEvent) {
            events.add(of(context, RuntimeEventType.ANSWER_STARTED, "开始生成回答", null, Map.of(), elapsedMs));
        } else if (event instanceof TextBlockDeltaEvent e) {
            events.add(of(context, RuntimeEventType.ANSWER_DELTA, "回答增量", e.getDelta(), Map.of(), elapsedMs));
        } else if (event instanceof TextBlockEndEvent) {
            events.add(of(context, RuntimeEventType.ANSWER_FINISHED, "回答生成完成", null, Map.of(), elapsedMs));
        } else if (event instanceof ThinkingBlockStartEvent) {
            events.add(of(context, RuntimeEventType.THINKING_STARTED, "开始思考", null, Map.of(), elapsedMs));
        } else if (event instanceof ThinkingBlockDeltaEvent e) {
            String content = exposeThinking ? e.getDelta() : null;
            events.add(of(context, RuntimeEventType.THINKING_DELTA, "思考增量", content,
                    Map.of("omitted", !exposeThinking, "chars", e.getDelta() != null ? e.getDelta().length() : 0), elapsedMs));
        } else if (event instanceof ThinkingBlockEndEvent) {
            events.add(of(context, RuntimeEventType.THINKING_FINISHED, "思考结束", null, Map.of(), elapsedMs));
        } else if (event instanceof ToolCallStartEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_CALL_STARTED, "开始准备工具调用", null,
                    toolMetadata(e.getToolCallId(), e.getToolCallName()), elapsedMs));
        } else if (event instanceof ToolCallDeltaEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_CALL_ARGS_DELTA, "工具参数增量", e.getDelta(),
                    toolMetadata(e.getToolCallId(), null), elapsedMs));
        } else if (event instanceof ToolCallEndEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_CALL_FINISHED, "工具调用参数生成完成", null,
                    toolMetadata(e.getToolCallId(), null), elapsedMs));
        } else if (event instanceof ToolResultStartEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_RESULT_STARTED, "开始返回工具结果", null,
                    toolMetadata(e.getToolCallId(), e.getToolCallName()), elapsedMs));
        } else if (event instanceof ToolResultTextDeltaEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_RESULT_DELTA, "工具结果文本增量", e.getDelta(),
                    toolMetadata(e.getToolCallId(), null), elapsedMs));
        } else if (event instanceof ToolResultDataDeltaEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_RESULT_DATA_DELTA, "工具结果数据增量", String.valueOf(e.getData()),
                    toolMetadata(e.getToolCallId(), null), elapsedMs));
        } else if (event instanceof ToolResultEndEvent e) {
            events.add(of(context, RuntimeEventType.TOOL_RESULT_FINISHED, "工具结果返回完成", null,
                    toolResultMetadata(e.getToolCallId(), e.getState()), elapsedMs));
        } else if (event instanceof ExceedMaxItersEvent e) {
            events.add(of(context, RuntimeEventType.RUNTIME_WARNING, "超过最大循环次数", null,
                    Map.of("currentIter", e.getCurrentIter(), "maxIters", e.getMaxIters()), elapsedMs));
        } else if (event instanceof RequireUserConfirmEvent e) {
            events.add(of(context, RuntimeEventType.CONFIRMATION_REQUIRED, "需要用户确认",
                    buildConfirmContent(e.getToolCalls()), requireConfirmMetadata(e), elapsedMs));
        } else if (event instanceof UserConfirmResultEvent e) {
            events.add(of(context, RuntimeEventType.CONFIRMATION_RESULT, "用户确认结果",
                    "用户确认结果已返回 AgentScope", userConfirmMetadata(e), elapsedMs));
        } else {
            events.add(translateBySimpleName(event, context, elapsedMs));
        }

        return events;
    }

    private RuntimeEvent translateBySimpleName(AgentEvent event, RuntimeContext context, long elapsedMs) {
        String simpleName = event.getClass().getSimpleName();
        if ("RequireUserConfirmEvent".equals(simpleName)) {
            return of(context, RuntimeEventType.CONFIRMATION_REQUIRED, "需要用户确认",
                    event.toString(), Map.of("sourceEvent", simpleName), elapsedMs);
        }
        if ("UserConfirmResultEvent".equals(simpleName)) {
            return of(context, RuntimeEventType.CONFIRMATION_RESULT, "用户确认结果",
                    event.toString(), Map.of("sourceEvent", simpleName), elapsedMs);
        }
        if ("RequireExternalExecutionEvent".equals(simpleName)) {
            return of(context, RuntimeEventType.EXTERNAL_EXECUTION_REQUIRED, "需要外部执行",
                    event.toString(), Map.of("sourceEvent", simpleName), elapsedMs);
        }
        if ("ExternalExecutionResultEvent".equals(simpleName)) {
            return of(context, RuntimeEventType.EXTERNAL_EXECUTION_RESULT, "外部执行结果",
                    event.toString(), Map.of("sourceEvent", simpleName), elapsedMs);
        }
        if ("RequestStopEvent".equals(simpleName)) {
            return of(context, RuntimeEventType.RUNTIME_WARNING, "收到停止请求",
                    event.toString(), Map.of("sourceEvent", simpleName), elapsedMs);
        }
        return of(context, RuntimeEventType.RAW_EVENT, "未分类的 AgentScope 事件",
                event.toString(), Map.of("sourceEvent", simpleName, "eventType", safe(event.getType())), elapsedMs);
    }

    private Map<String, Object> requireConfirmMetadata(RequireUserConfirmEvent event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceEvent", event.getClass().getSimpleName());
        metadata.put("requestType", "TOOL_PERMISSION");
        metadata.put("replyId", safe(event.getReplyId()));
        metadata.put("toolCalls", toolCalls(event.getToolCalls()));
        metadata.put("riskLevel", highestRisk(event.getToolCalls()));
        return metadata;
    }

    private Map<String, Object> userConfirmMetadata(UserConfirmResultEvent event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceEvent", event.getClass().getSimpleName());
        metadata.put("replyId", safe(event.getReplyId()));
        metadata.put("confirmCount", event.getConfirmResults() != null ? event.getConfirmResults().size() : 0);
        return metadata;
    }

    private List<Map<String, Object>> toolCalls(List<ToolUseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolUseBlock block : blocks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", safe(block.getId()));
            item.put("name", safe(block.getName()));
            item.put("input", block.getInput() != null ? block.getInput() : Map.of());
            item.put("content", safe(block.getContent()));
            item.put("metadata", block.getMetadata() != null ? block.getMetadata() : Map.of());
            item.put("state", safe(block.getState()));
            item.put("riskLevel", classifyRisk(block.getName()));
            result.add(item);
        }
        return result;
    }

    private String buildConfirmContent(List<ToolUseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "AgentScope 请求用户确认工具调用";
        }
        if (blocks.size() == 1) {
            return "AgentScope 请求确认执行工具：" + safe(blocks.get(0).getName());
        }
        return "AgentScope 请求确认执行 " + blocks.size() + " 个工具调用";
    }

    private String highestRisk(List<ToolUseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "MEDIUM";
        }
        for (ToolUseBlock block : blocks) {
            if ("HIGH".equals(classifyRisk(block.getName()))) {
                return "HIGH";
            }
        }
        return "MEDIUM";
    }

    private String classifyRisk(String toolName) {
        if ("apply_patch".equals(toolName) || "Edit".equals(toolName)
                || "write_file".equals(toolName) || "Write".equals(toolName)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private Map<String, Object> toolMetadata(Object toolCallId, Object toolName) {
        String callId = safe(toolCallId);
        String name = safe(toolName);
        return Map.of(
                "toolCallId", callId,
                "callId", callId,
                "tool", name,
                "toolName", name
        );
    }

    private Map<String, Object> toolResultMetadata(Object toolCallId, Object state) {
        String callId = safe(toolCallId);
        return Map.of(
                "toolCallId", callId,
                "callId", callId,
                "state", safe(state)
        );
    }
    private RuntimeEvent of(RuntimeContext context, RuntimeEventType type, String stage, String content,
                            Map<String, Object> metadata, long elapsedMs) {
        return RuntimeEvent.of(context.getRunId(), context.getTraceId(), type, stage, content, metadata, elapsedMs);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

