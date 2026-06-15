package com.agentplatform.runtime.agentscope;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
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
            answer.append(textBlockDeltaEvent.getDelta());
        }
        if (event instanceof RequireUserConfirmEvent) {
            confirmationRequired = true;
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
}
