package com.archive.agent;

import com.archive.common.ApiResponse;
import com.archive.dto.TurnRequest;
import com.archive.qaagent.QaAgentClient;
import com.archive.qaagent.QaAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 多轮对话端点 — 优先转发 Python qa-agent.
 */
@Slf4j
@RestController
@RequestMapping("/api/qa/turn")
@RequiredArgsConstructor
public class MultiTurnController {

    private final QaAgentProperties qaAgentProperties;

    @Autowired(required = false)
    private QaAgentClient qaAgentClient;

    @Autowired(required = false)
    private AgentEngine agentEngine;

    @Value("${spring.ai.agent.enabled:false}")
    private boolean javaAgentEnabled;

    @GetMapping("/{sessionId}")
    public ApiResponse<AgentResponse> askGet(
            @PathVariable String sessionId,
            @RequestParam String question) {
        return ask(sessionId, question);
    }

    @PostMapping("/{sessionId}")
    public ApiResponse<AgentResponse> askPost(
            @PathVariable String sessionId,
            @RequestBody TurnRequest body) {
        return ask(sessionId, body.getQuestion());
    }

    private ApiResponse<AgentResponse> ask(String sessionId, String question) {
        log.info("[MultiTurn] sessionId={}, question={}", sessionId, question);

        // 路径 1: Python qa-agent
        if (qaAgentProperties.isEnabled() && qaAgentClient != null) {
            try {
                AgentResponse resp = qaAgentClient.ask(question, sessionId);
                return ApiResponse.ok(resp);
            } catch (Exception e) {
                log.warn("Python qa-agent turn 失败, 降级到 Java AgentEngine", e);
                // 不抛异常，降级到路径 2
            }
        }

        // 路径 2: Java AgentEngine（降级）
        if (javaAgentEnabled && agentEngine != null) {
            AgentRequest req = new AgentRequest();
            req.setSessionId(sessionId);
            req.setQuestion(question);
            return ApiResponse.ok(agentEngine.run(req));
        }

        throw new IllegalStateException("多轮问答未启用，请配置 spring.ai.agent.enabled=true 或 app.qa-agent.enabled=true");
    }
}
