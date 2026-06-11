package com.archive.agent;

import java.util.List;

public class AgentResponse {
    private String answer;
    private List<AgentStep> steps;
    private List<String> sources;
    private boolean agentMode;
    /** v1.1: 隐式切换 hint, null = v1.0 路径. */
    private String projectSwitchHint;
    /** v1.1: AI_INFERRED / PENDING_REVIEW, null = 不显示. */
    private String confidenceBadge;
    
    public AgentResponse() {}
    
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<AgentStep> getSteps() { return steps; }
    public void setSteps(List<AgentStep> steps) { this.steps = steps; }
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
    public boolean isAgentMode() { return agentMode; }
    public void setAgentMode(boolean agentMode) { this.agentMode = agentMode; }
    public String getProjectSwitchHint() { return projectSwitchHint; }
    public void setProjectSwitchHint(String projectSwitchHint) { this.projectSwitchHint = projectSwitchHint; }
    public String getConfidenceBadge() { return confidenceBadge; }
    public void setConfidenceBadge(String confidenceBadge) { this.confidenceBadge = confidenceBadge; }
}
