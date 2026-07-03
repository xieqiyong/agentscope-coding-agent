package com.agentplatform.runtime.multiagent;

/**
 * RouterAgent 输出的路由决策。
 * 中文注释：路由结果只决定下一跳，真正执行仍由 Orchestrator 和平台治理控制。
 */
public class AgentRouteDecision {

    public static final String ROUTE_DIRECT_ANSWER = "DIRECT_ANSWER";
    public static final String ROUTE_SINGLE_AGENT = "SINGLE_AGENT";
    public static final String ROUTE_PLAN_ONLY = "PLAN_ONLY";
    public static final String ROUTE_PLAN_EXECUTE = "PLAN_EXECUTE";

    private String route = ROUTE_SINGLE_AGENT;
    private String intent = "GENERAL";
    private String reason = "";
    private String riskLevel = "LOW";
    private double confidence = 0.5;
    private boolean requiresWorkspaceEvidence;
    private boolean requiresReview;

    public static AgentRouteDecision fallback(String reason) {
        AgentRouteDecision decision = new AgentRouteDecision();
        decision.setRoute(ROUTE_SINGLE_AGENT);
        decision.setIntent("FALLBACK");
        decision.setReason(reason);
        decision.setRiskLevel("LOW");
        decision.setConfidence(0.4);
        return decision;
    }

    public String effectiveRoute() {
        if (ROUTE_DIRECT_ANSWER.equalsIgnoreCase(route)) {
            return ROUTE_DIRECT_ANSWER;
        }
        if (ROUTE_PLAN_ONLY.equalsIgnoreCase(route)) {
            return ROUTE_PLAN_ONLY;
        }
        if (ROUTE_PLAN_EXECUTE.equalsIgnoreCase(route)) {
            return ROUTE_PLAN_EXECUTE;
        }
        return ROUTE_SINGLE_AGENT;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isRequiresWorkspaceEvidence() {
        return requiresWorkspaceEvidence;
    }

    public void setRequiresWorkspaceEvidence(boolean requiresWorkspaceEvidence) {
        this.requiresWorkspaceEvidence = requiresWorkspaceEvidence;
    }

    public boolean isRequiresReview() {
        return requiresReview;
    }

    public void setRequiresReview(boolean requiresReview) {
        this.requiresReview = requiresReview;
    }
}
