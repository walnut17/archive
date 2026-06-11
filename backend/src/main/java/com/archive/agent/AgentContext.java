package com.archive.agent;

import com.archive.agent.tool.ToolResult;
import com.archive.common.SwitchDecision;
import java.util.*;

public class AgentContext {
    private String question;
    private List<AgentStep> steps = new ArrayList<>();
    private String projectCode;
    private SwitchDecision lastSwitchDecision;
    private Map<String, Object> state = new HashMap<>();
    
    public AgentContext() {}
    
    public AgentContext(String question) {
        this.question = question;
    }
    
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<AgentStep> getSteps() { return steps; }
    public void setSteps(List<AgentStep> steps) { this.steps = steps; }
    public void addStep(AgentStep step) { this.steps.add(step); }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    /** 当前会话锁定的项目编号 (同 projectCode). */
    public String getLockedProjectCode() { return projectCode; }
    public SwitchDecision getLastSwitchDecision() { return lastSwitchDecision; }
    public void setLastSwitchDecision(SwitchDecision lastSwitchDecision) {
        this.lastSwitchDecision = lastSwitchDecision;
    }
    public Map<String, Object> getState() { return state; }
    public void setState(Map<String, Object> state) { this.state = state; }
}
