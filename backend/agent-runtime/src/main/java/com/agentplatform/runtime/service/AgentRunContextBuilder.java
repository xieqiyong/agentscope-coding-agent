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
    private MemoryContextAssembler memoryContextAssembler;

    public RuntimeContext build(AgentRunCommand command, Long conversationId, Long userMessageId,
                                Long runId, String traceId) {
        WorkspaceEntity workspace = workspaceRepository.findById(command.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(404, "工作区不存在"));
        AgentEntity agent = agentRepository.findById(command.getAgentId())
                .orElseThrow(() -> new BusinessException(404, "智能体不存在"));

        ModelConfigEntity modelConfig = null;
        if (agent.getModelConfigId() != null) {
            modelConfig = modelConfigRepository.findById(agent.getModelConfigId()).orElse(null);
        }

        String userId = StringUtils.hasText(command.getUserId()) ? command.getUserId() : "default";
        List<MemoryEntryEntity> activeMemories = memoryContextAssembler.loadActiveMemories(workspace.getId(), userId);

        List<ConversationMessageEntity> recentMessages =
                conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

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
        context.setModelBaseUrl(firstNonBlank(command.getModelBaseUrl(), modelConfig != null ? modelConfig.getBaseUrl() : null));
        context.setModelName(firstNonBlank(command.getModelName(), modelConfig != null ? modelConfig.getModelName() : null));
        // 第一版暂时直接使用命令中的 key 或配置密文字段；后续应接入真正的密钥解密服务。
        context.setApiKey(firstNonBlank(command.getApiKey(), modelConfig != null ? modelConfig.getApiKeyCipher() : null));
        context.setMaxIterations(firstPositive(command.getMaxIterations(), agent.getMaxIterations(), 8));
        context.setTimeoutSeconds(firstPositive(command.getTimeoutSeconds(), agent.getTimeoutSeconds(), 120));
        context.setSystemPrompt(buildSystemPrompt(agent, workspace, activeMemories));
        return context;
    }

    private String buildSystemPrompt(AgentEntity agent, WorkspaceEntity workspace, List<MemoryEntryEntity> memories) {
        String basePrompt = StringUtils.hasText(agent.getSystemPrompt())
                ? agent.getSystemPrompt()
                : "你是一个严谨的通用编码智能体。先判断用户请求是否依赖当前工作区；依赖项目事实时要查证据，不依赖项目事实时可以直接回答。";

        basePrompt = basePrompt + buildCurrentTimePrompt();

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
                + "3. 新建文件、整文件替换或大文件修改时，优先使用 Write 或 write_file。\n"
                + "4. 多文件任务必须拆成多个工具调用，不要让用户手动复制粘贴文件内容。\n"
                + "5. propose_patch 和 propose_file_change 只保存审核提案，不会直接写入磁盘；只有用户明确要求审核、修改敏感文件或高风险变更时才使用。\n"
                + "6. 只有 Write、write_file、Edit 或 apply_patch 返回成功后，才能声称文件已经创建或修改完成。\n"
                + "7. 直接写入工具可能会被平台权限治理暂停并要求用户确认；如果工具结果显示被拒绝或等待确认，不要声称修改已经完成。\n"
                + "8. Bash、Notebook、子 Agent、任务编排暂未开放，不要假装已经执行这些工具。";

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
                4. 不要读取或输出密钥、token、证书、私钥等敏感内容。
                5. 工具结果优先于模型猜测；如果证据不足，要明确说明。
                6. 上方偏好和约束是隐式协作规则，按规则行动即可；不要在回答中主动解释这些规则来自哪里，也不要主动说已经保存或记录，除非用户明确询问已知偏好、项目约束或要求确认保存结果。
                """.formatted(basePrompt, workspace.getName(), workspace.getRootPath(), memoryText);
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
