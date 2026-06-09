package com.archive.agent;

import com.archive.dto.QaRequest;

import java.util.List;
import java.util.Map;

public class AgentRequest {
    private String question;
    private List<Map<String, String>> history;
    private String sessionId;
    
    public AgentRequest() {}
    
    public AgentRequest(String question) {
        this.question = question;
    }
    
    /**
     * 从 QaRequest 转换为 AgentRequest.
     */
    public static AgentRequest fromQaRequest(QaRequest req) {
        AgentRequest ar = new AgentRequest();
        ar.question = req.getQuestion();
        return ar;
    }
    
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
