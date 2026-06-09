package com.archive.agent;

import java.util.List;
import java.util.Map;

public class AgentRequest {
    private String question;
    private List<Map<String, String>> history;
    private String sessionId;
    
    public AgentRequest() {}
    
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
