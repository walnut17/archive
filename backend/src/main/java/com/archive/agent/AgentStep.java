package com.archive.agent;

public class AgentStep {
    private int iteration;
    private String thought;
    private String tool;
    private String toolArgs;
    private String observation;
    
    public AgentStep() {}
    
    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }
    public String getThought() { return thought; }
    public void setThought(String thought) { this.thought = thought; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getToolArgs() { return toolArgs; }
    public void setToolArgs(String toolArgs) { this.toolArgs = toolArgs; }
    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }
}
