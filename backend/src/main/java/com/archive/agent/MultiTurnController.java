package com.archive.agent;

import com.archive.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * I-13: 多轮对话端点.
 *
 * GET /api/qa/turn/{sessionId}?question=...
 * 客户端传 sessionId (来自前端的 chat panel), 后端自动从 chat_memory 加载历史
 *
 * 跟 /api/qa/ask 区别:
 * - /api/qa/ask 走单次 Q&A, 不带 session
 * - /api/qa/turn/{sessionId} 走多轮, 自动带历史
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/qa/turn")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.agent.enabled", havingValue = "true", matchIfMissing = true)
public class MultiTurnController {

    private final AgentEngine agentEngine;

    @GetMapping("/{sessionId}")
    public ApiResponse<AgentResponse> ask(
            @PathVariable String sessionId,
            @RequestParam String question) {
        log.info("[MultiTurn] sessionId={}, question={}", sessionId, question);
        AgentRequest req = new AgentRequest();
        req.setSessionId(sessionId);
        req.setQuestion(question);
        AgentResponse resp = agentEngine.run(req);
        return ApiResponse.ok(resp);
    }
}
