package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.AgentRunEntity;
import com.agentplatform.persistence.enums.AgentRunStatus;
import com.agentplatform.persistence.repository.AgentRunRepository;
import com.agentplatform.runtime.model.AgentRunCommand;
import com.agentplatform.runtime.model.AgentRunResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Agent Run 生命周期服务。
 * 所有 agent_runs.status 的修改都应该从这里进入，避免业务代码绕过状态机。
 */
@Service
public class AgentRunLifecycleService {

    @Resource
    private AgentRunRepository agentRunRepository;

    @Resource
    private AgentRunStateMachine stateMachine;

    public AgentRunEntity startRun(AgentRunCommand command, Long conversationId, Long userMessageId, String traceId) {
        AgentRunEntity run = new AgentRunEntity();
        run.setTraceId(traceId);
        run.setConversationId(conversationId);
        run.setAgentId(command.getAgentId());
        run.setWorkspaceId(command.getWorkspaceId());
        run.setUserMessageId(userMessageId);
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setInputTokens(0);
        run.setOutputTokens(0);
        run.setStartedAt(LocalDateTime.now());
        return agentRunRepository.save(run);
    }

    public AgentRunEntity waitForApproval(Long runId) {
        return transit(runId, AgentRunStatus.WAITING_APPROVAL, null, null);
    }

    public AgentRunEntity resumeFromApproval(Long runId) {
        return transit(runId, AgentRunStatus.RUNNING, null, null);
    }

    public AgentRunEntity completeRun(Long runId, AgentRunResult result) {
        return transit(runId, AgentRunStatus.COMPLETED, result, null);
    }

    public AgentRunEntity failRun(Long runId, String errorMessage) {
        return transit(runId, AgentRunStatus.FAILED, null, errorMessage);
    }

    public AgentRunEntity timeoutRun(Long runId, String errorMessage) {
        return transit(runId, AgentRunStatus.TIMEOUT, null, errorMessage);
    }

    public AgentRunEntity cancelRun(Long runId, String reason) {
        return transit(runId, AgentRunStatus.CANCELLED, null, reason);
    }

    private AgentRunEntity transit(Long runId, AgentRunStatus target, AgentRunResult result, String errorMessage) {
        AgentRunEntity run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(404, "Agent Run 不存在：" + runId));

        AgentRunStatus source = AgentRunStatus.from(run.getStatus());
        if (source == target) {
            fillResult(run, target, result, errorMessage);
            return agentRunRepository.save(run);
        }

        // 中文注释：终态优先，避免后台任务晚到的完成/失败事件覆盖用户取消或超时结果。
        if (source.isTerminal()) {
            return run;
        }

        stateMachine.assertCanTransit(source, target);
        run.setStatus(target.name());
        fillResult(run, target, result, errorMessage);
        return agentRunRepository.save(run);
    }

    private void fillResult(AgentRunEntity run, AgentRunStatus target, AgentRunResult result, String errorMessage) {
        if (target.isTerminal() && run.getFinishedAt() == null) {
            run.setFinishedAt(LocalDateTime.now());
        }
        if (errorMessage != null) {
            run.setErrorMessage(errorMessage);
        }
        if (result != null) {
            run.setInputTokens(result.getInputTokens());
            run.setOutputTokens(result.getOutputTokens());
        }
    }
}
