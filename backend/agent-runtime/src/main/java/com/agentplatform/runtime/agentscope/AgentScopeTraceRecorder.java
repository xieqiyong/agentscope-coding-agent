package com.agentplatform.runtime.agentscope;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.model.ChatUsage;

import java.util.List;

/**
 * AgentScope 事件记录器。
 * 它通过 Reactor doOnNext 被动观察事件，不在这里做权限拦截。
 */
class AgentScopeTraceRecorder {

    private final RuntimeContext context;
    private final RuntimeEventSink sink;
    private final AgentScopeEventTranslator translator = new AgentScopeEventTranslator();
    private final DsmlTextFilter dsmlTextFilter = new DsmlTextFilter();
    private final long startedNanos = System.nanoTime();
    private final StringBuilder answer = new StringBuilder();
    private int modelCallCount;
    private int inputTokens;
    private int outputTokens;
    private boolean confirmationRequired;

    AgentScopeTraceRecorder(RuntimeContext context, RuntimeEventSink sink) {
        this.context = context;
        this.sink = sink;
    }

    void record(AgentEvent event) {
        if (event == null) {
            return;
        }
        if (event instanceof ModelCallStartEvent) {
            modelCallCount++;
        }
        if (event instanceof ModelCallEndEvent modelCallEndEvent) {
            addUsage(modelCallEndEvent.getUsage());
        }
        if (event instanceof TextBlockDeltaEvent textBlockDeltaEvent && textBlockDeltaEvent.getDelta() != null) {
            String visibleDelta = dsmlTextFilter.accept(textBlockDeltaEvent.getDelta());
            if (!visibleDelta.isEmpty()) {
                answer.append(visibleDelta);
                sink.emit(RuntimeEvent.of(
                        context.getRunId(),
                        context.getTraceId(),
                        com.agentplatform.runtime.model.RuntimeEventType.ANSWER_DELTA,
                        "回答增量",
                        visibleDelta,
                        java.util.Map.of(),
                        elapsedMs()
                ));
            }
            return;
        }
        if (event instanceof TextBlockEndEvent) {
            String visibleDelta = dsmlTextFilter.flush();
            if (!visibleDelta.isEmpty()) {
                answer.append(visibleDelta);
                sink.emit(RuntimeEvent.of(
                        context.getRunId(),
                        context.getTraceId(),
                        com.agentplatform.runtime.model.RuntimeEventType.ANSWER_DELTA,
                        "回答增量",
                        visibleDelta,
                        java.util.Map.of(),
                        elapsedMs()
                ));
            }
        }
        if (event instanceof RequireUserConfirmEvent) {
            // 中文注释：工具审批已关闭；底层意外产生确认事件时不再转成平台审批流。
            return;
        }

        boolean exposeThinking = Boolean.TRUE.equals(context.getCommand().getTraceThinkingContent());
        List<RuntimeEvent> events = translator.translate(event, context, elapsedMs(), exposeThinking);
        for (RuntimeEvent runtimeEvent : events) {
            sink.emit(runtimeEvent);
        }
    }

    String answer() {
        return answer.toString().trim();
    }

    int getModelCallCount() {
        return modelCallCount;
    }

    boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    int inputTokensOrEstimate(String input) {
        return inputTokens > 0 ? inputTokens : estimateTokens(input);
    }

    int outputTokensOrEstimate(String output) {
        return outputTokens > 0 ? outputTokens : estimateTokens(output);
    }

    private void addUsage(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        inputTokens += usage.getInputTokens();
        outputTokens += usage.getOutputTokens();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private long elapsedMs() {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static class DsmlTextFilter {

        private static final String OPEN = "<｜｜DSML｜｜tool_calls>";
        private static final String CLOSE = "</｜｜DSML｜｜tool_calls>";
        private static final int KEEP = Math.max(OPEN.length(), CLOSE.length()) - 1;

        private final StringBuilder pending = new StringBuilder();
        private boolean insideToolCalls;

        private String accept(String delta) {
            pending.append(delta);
            StringBuilder visible = new StringBuilder();
            while (pending.length() > 0) {
                if (insideToolCalls) {
                    int closeIndex = pending.indexOf(CLOSE);
                    if (closeIndex < 0) {
                        keepPossibleCloseSuffix();
                        break;
                    }
                    pending.delete(0, closeIndex + CLOSE.length());
                    insideToolCalls = false;
                    continue;
                }

                int openIndex = pending.indexOf(OPEN);
                if (openIndex >= 0) {
                    visible.append(pending, 0, openIndex);
                    pending.delete(0, openIndex + OPEN.length());
                    insideToolCalls = true;
                    continue;
                }

                int emitLength = safeEmitLength();
                if (emitLength <= 0) {
                    break;
                }
                visible.append(pending, 0, emitLength);
                pending.delete(0, emitLength);
            }
            return visible.toString();
        }

        private String flush() {
            if (insideToolCalls) {
                pending.setLength(0);
                return "";
            }
            String text = pending.toString();
            pending.setLength(0);
            return text;
        }

        private int safeEmitLength() {
            int keepLength = possibleOpenPrefixLength();
            return pending.length() - keepLength;
        }

        private int possibleOpenPrefixLength() {
            int max = Math.min(KEEP, pending.length());
            for (int len = max; len > 0; len--) {
                String suffix = pending.substring(pending.length() - len);
                if (OPEN.startsWith(suffix)) {
                    return len;
                }
            }
            return 0;
        }

        private void keepPossibleCloseSuffix() {
            if (pending.length() <= KEEP) {
                return;
            }
            pending.delete(0, pending.length() - KEEP);
        }
    }
}
