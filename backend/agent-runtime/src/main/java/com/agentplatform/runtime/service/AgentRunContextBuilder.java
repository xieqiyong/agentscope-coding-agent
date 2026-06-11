package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.AgentEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.entity.MemoryEntryEntity;
import com.agentplatform.persistence.entity.ModelConfigEntity;
import com.agentplatform.persistence.entity.WorkspaceEntity;
import com.agentplatform.persistence.repository.AgentRepository;
import com.agentplatform.persistence.repository.ConversationMessageRepository;
import com.agentplatform.persistence.repository.MemoryEntryRepository;
import com.agentplatform.persistence.repository.ModelConfigRepository;
import com.agentplatform.persistence.repository.WorkspaceRepository;
import com.agentplatform.runtime.model.AgentRunCommand;
import com.agentplatform.runtime.model.RuntimeContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private MemoryEntryRepository memoryEntryRepository;

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
        List<MemoryEntryEntity> activeMemories =
                memoryEntryRepository.findByWorkspaceIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                        workspace.getId(), userId, "ACTIVE");

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
                : "你是一个严谨的 Java 编码智能体，必须先理解项目上下文，再使用工具读取文件和提出修改方案。";

        StringBuilder memoryText = new StringBuilder();
        int count = 0;
        for (MemoryEntryEntity memory : memories) {
            if (count >= 12) {
                break;
            }
            if (!StringUtils.hasText(memory.getContent())) {
                continue;
            }
            memoryText.append("- [")
                    .append(memory.getMemoryType())
                    .append("] ")
                    .append(memory.getContent())
                    .append("\n");
            count++;
        }
        if (memoryText.isEmpty()) {
            memoryText.append("- 暂无可注入的长期记忆。\n");
        }

        return """
                %s

                【工作区】
                - 名称：%s
                - 根目录：%s

                【长期记忆】
                %s
                【运行规则】
                1. 你只能通过工具读取工作区文件，不要臆测文件内容。
                2. 所有文件路径都必须视为相对工作区路径。
                3. 修改代码时只能提出 unified diff，不要假装已经写入文件。
                4. 不要读取或输出密钥、token、证书、私钥等敏感内容。
                5. 工具结果优先于模型猜测；如果证据不足，要明确说明。
                """.formatted(basePrompt, workspace.getName(), workspace.getRootPath(), memoryText);
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

