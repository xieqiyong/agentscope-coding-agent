package com.agentplatform.runtime.multiagent;

import java.util.ArrayList;
import java.util.List;

/**
 * PlannerAgent 输出的结构化计划。
 */
public class AgentPlan {

    /**
     * 图协议版本。1 表示旧的线性计划，2 表示带依赖关系的任务图。
     */
    private int graphVersion = 1;

    /**
     * 单次任务图允许同时运行的最大节点数。
     */
    private int maxConcurrency = 1;

    private String title;
    private String summary;
    private String riskLevel = "MEDIUM";
    private List<AgentPlanStep> steps = new ArrayList<>();
    private List<String> acceptanceCriteria = new ArrayList<>();
    private List<String> expectedTools = new ArrayList<>();
    private boolean requiresApproval = false;

    public int getGraphVersion() {
        return graphVersion;
    }

    public void setGraphVersion(int graphVersion) {
        this.graphVersion = graphVersion;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<AgentPlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentPlanStep> steps) {
        this.steps = steps;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public List<String> getExpectedTools() {
        return expectedTools;
    }

    public void setExpectedTools(List<String> expectedTools) {
        this.expectedTools = expectedTools;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }
}
