package com.agentplatform.runtime.agentscope;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 上下文压缩中间件。
 * 中文注释：ReAct 循环每轮都会把全部历史（含越来越大的工具结果）重新喂给模型，
 * 这里在每次模型调用前估算输入规模，超过阈值时把较早的历史用同模型压缩成一段摘要，
 * 只保留系统提示、摘要和最近几条消息，避免长任务中输入 token 失控增长。
 */
class ContextCompressionMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionMiddleware.class);

    /**
     * 触发压缩的估算 token 阈值（估算规则：总字符数 / 4）
     */
    private final int thresholdTokens;

    /**
     * 压缩时保留最近多少条消息不动
     */
    private final int keepRecentMessages;

    /**
     * 上一次压缩生成的摘要；再次超阈值时把旧摘要与新淘汰段落一起增量压缩
     */
    private String previousSummary;

    /**
     * 单条消息进入摘要时的截断长度，避免超长工具结果把摘要请求本身撑爆
     */
    private static final int PER_MESSAGE_CHAR_LIMIT = 2000;

    /**
     * 摘要调用超时时间
     */
    private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(60);

    ContextCompressionMiddleware(int thresholdTokens, int keepRecentMessages) {
        this.thresholdTokens = thresholdTokens;
        this.keepRecentMessages = keepRecentMessages;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext runtimeContext,
                                        ModelCallInput input,
                                        java.util.function.Function<ModelCallInput, Flux<AgentEvent>> next) {
        List<Msg> messages = input.messages();
        if (messages == null || estimateTokens(messages) <= thresholdTokens) {
            return next.apply(input);
        }
        try {
            List<Msg> compressed = compress(input);
            log.info("上下文压缩：{} 条消息压缩为 {} 条，估算 token {} -> {}",
                    messages.size(), compressed.size(), estimateTokens(messages), estimateTokens(compressed));
            return next.apply(new ModelCallInput(compressed, input.tools(), input.options(), input.model()));
        } catch (Exception e) {
            // 压缩失败绝不阻塞主链路：原文放行，仅记录告警
            log.warn("上下文压缩失败，已降级为原文放行：{}", e.getMessage());
            return next.apply(input);
        }
    }

    /**
     * 压缩消息列表：系统提示 + 摘要消息 + 最近 N 条。
     */
    private List<Msg> compress(ModelCallInput input) {
        List<Msg> messages = input.messages();
        int keepCount = Math.min(keepRecentMessages, messages.size());
        List<Msg> tail = messages.subList(messages.size() - keepCount, messages.size());
        List<Msg> head = messages.subList(0, messages.size() - keepCount);

        String summaryText = summarize(input, head);

        List<Msg> compressed = new ArrayList<>();
        for (Msg message : head) {
            if (message.getRole() == MsgRole.SYSTEM) {
                compressed.add(message);
            }
        }
        compressed.add(new UserMessage(
                "【此前对话与工具结果的压缩摘要】\n" + summaryText
                        + "\n（以上是本任务早前过程的要点，原始明细已压缩省略；如需具体文件内容请重新读取）"));
        compressed.addAll(tail);
        return compressed;
    }

    /**
     * 调用同一个模型把被淘汰的历史段压缩成要点。
     * 中文注释：摘要调用直接走 model.stream，不经过 Agent 循环，因此不会再次触发本中间件。
     */
    private String summarize(ModelCallInput input, List<Msg> head) {
        StringBuilder task = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            task.append("[此前已有摘要]\n").append(previousSummary).append("\n\n[需要合并的新增内容]\n");
        }
        for (Msg message : head) {
            if (message.getRole() == MsgRole.SYSTEM) {
                continue;
            }
            String text = message.getTextContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.length() > PER_MESSAGE_CHAR_LIMIT) {
                text = text.substring(0, PER_MESSAGE_CHAR_LIMIT) + "…(截断)";
            }
            task.append('[').append(message.getRole()).append("] ").append(text).append("\n");
        }
        String summaryPrompt = "把下面的多轮对话与工具结果历史压缩成执行要点，供继续完成任务时参考。"
                + "必须保留：1) 任务最终目标与当前进度；2) 已读取的关键文件路径和得出的重要结论；"
                + "3) 已执行过的修改或命令及其结果；4) 尚未解决的问题。"
                + "用简洁条目输出，总长度不超过 600 字，不要寒暄，不要复述原文。\n\n"
                + task;

        AtomicReference<String> summaryRef = new AtomicReference<>("");
        input.model().stream(List.of(new UserMessage(summaryPrompt)), List.of(), null)
                .doOnNext(response -> appendResponseText(summaryRef, response))
                .blockLast(SUMMARY_TIMEOUT);

        String summary = summaryRef.get().trim();
        if (summary.isBlank()) {
            throw new IllegalStateException("摘要模型返回为空");
        }
        previousSummary = summary;
        return summary;
    }

    private void appendResponseText(AtomicReference<String> ref, ChatResponse response) {
        if (response.getContent() == null) {
            return;
        }
        for (Object block : response.getContent()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                ref.set(ref.get() + textBlock.getText());
            }
        }
    }

    private int estimateTokens(List<Msg> messages) {
        int chars = 0;
        for (Msg message : messages) {
            String text = message.getTextContent();
            if (text != null) {
                chars += text.length();
            }
        }
        // 粗略估算：英文约 4 字符一个 token；中文偏保守可接受
        return chars / 4;
    }
}
