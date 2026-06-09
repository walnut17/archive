package com.archive.agent;

import java.util.List;

public class AgentResponse {
    private String answer;
    private List<AgentStep> steps;
    private List<String> sources;
    private boolean agentMode;
    
    public AgentResponse() {}
    
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<AgentStep> getSteps() { return steps; }
    public void setSteps(List<AgentStep> steps) { this.steps = steps; }
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
    public boolean isAgentMode() { return agentMode; }
    public void setAgentMode(boolean agentMode) { this.agentMode = agentMode; }
}
