package com.archive.agent;

import com.archive.agent.tool.ToolResult;
import java.util.*;

public class AgentContext {
    private String question;
    private List<AgentStep> steps = new ArrayList<>();
    private String projectCode;
    private Map<String, Object> state = new HashMap<>();
    
    public AgentContext() {}
    
    public AgentContext(String question) {
        this.question = question;
    }
    
    // Getters and setters
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<AgentStep> getSteps() { return steps; }
    public void setSteps(List<AgentStep> steps) { this.steps = steps; }
    public void addStep(AgentStep step) { this.steps.add(step); }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public Map<String, Object> getState() { return state; }
    public void setState(Map<String, Object> state) { this.state = state; }
}
