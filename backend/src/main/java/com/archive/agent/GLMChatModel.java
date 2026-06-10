package com.archive.agent;

import com.archive.service.GlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义 ChatModel, 包装项目自家的 GlmService.
 *
 * **为什么不用 spring-ai-starter-model-openai 调智谱?**
 * 智谱 OpenAI 兼容 endpoint 是 `/v4/chat/completions` (非标 v4, 不是 OpenAI 标准 v1)
 * Spring AI 1.1 OpenAI 兼容协议会硬拼 `/v1/chat/completions`, 拼成 `/v4/v1/chat/completions` 404
 *
 * 修法: 写个 ChatModel 实现, 直接调 GlmService (项目自家, 已经验过智谱 v4 endpoint 通)
 *
 * @author Mavis (修 Sisyphus P0-15 + 智谱 v4 不兼容问题)
 */
@Component
@Primary
@RequiredArgsConstructor
public class GLMChatModel implements ChatModel {

    private final GlmService glmService;

    @Override
    public ChatResponse call(Prompt prompt) {
        // 把 Prompt 拆成 system + user (GlmService 接口)
        String systemPrompt = "";
        StringBuilder userMsg = new StringBuilder();
        for (var msg : prompt.getInstructions()) {
            if (msg instanceof org.springframework.ai.chat.messages.SystemMessage sm) {
                systemPrompt = sm.getText();
            } else if (msg instanceof org.springframework.ai.chat.messages.UserMessage um) {
                userMsg.append(um.getText());
            } else if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
                // 多轮历史, 作为 user 拼上去
                userMsg.append("\n[历史] ").append(am.getText());
            } else {
                // 兜底: 拼 .getText()
                userMsg.append(msg.getText());
            }
        }
        String content = glmService.chat(systemPrompt, userMsg.toString());
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
