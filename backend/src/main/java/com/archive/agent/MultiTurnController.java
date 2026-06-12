package com.archive.agent;

import com.archive.common.ApiResponse;
import com.archive.dto.TurnRequest;
import com.archive.qaagent.QaAgentClient;
import com.archive.qaagent.QaAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

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

        // 路径 3: 双路径均不可用 → 返回 503 明确文案，不抛 500 裸栈
        log.warn("[MultiTurn] qa-agent 与 Java Agent 均不可用, 返回 503 fallback");
        AgentResponse fallback = new AgentResponse();
        fallback.setAnswer("问答服务暂不可用，请稍后重试。如需紧急帮助，请联系档案管理员。");
        fallback.setAgentMode(false);
        fallback.setSteps(Collections.emptyList());
        return ApiResponse.ok(fallback);
    }
}
