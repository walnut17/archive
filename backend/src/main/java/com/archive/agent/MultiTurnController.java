package com.archive.agent;

import com.archive.common.ApiResponse;
import com.archive.dto.QaRequest;
import com.archive.dto.QaResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

/**
 * 多轮对话 API(I-13).
 * 使用 sessionId 拉历史 + 拼上下文,实现连续对话.
 */
@RestController
@RequestMapping("/api/qa/turn")
public class MultiTurnController {

    private final ChatClient chatClient;
    private final MultiTurnService multiTurnService;

    public MultiTurnController(ChatClient.Builder builder, MultiTurnService multiTurnService) {
        this.chatClient = builder.build();
        this.multiTurnService = multiTurnService;
    }

    /**
     * 多轮对话端点.
     * 前端传 sessionId(首次可为 UUID),服务端自动拉历史.
     */
    @PostMapping("/{sessionId}")
    public ApiResponse<QaResponse> turn(
            @PathVariable String sessionId,
            @RequestBody QaRequest req) {

        // 用 sessionId 作为 conversationId 拉历史
        String answer = chatClient.prompt()
                .user(req.getQuestion())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();

        QaResponse qr = QaResponse.builder()
                .question(req.getQuestion())
                .answer(answer)
                .build();

        return ApiResponse.ok(qr);
    }
}
