package com.archive.agent;

import com.archive.service.GlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
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
 * **温度控制 (Mavis 修 P0-19)**: GLM-4-Flash-250414 是 8B 小模型, 默认 temperature=0.7
 * ReAct JSON 输出不稳, 测例 fail. 在这里读 ChatOptions 拿 temperature 0.1 让输出稳定.
 *
 * @author Mavis (修 Sisyphus P0-15 + P0-19)
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
                userMsg.append(msg.getText());
            }
        }

        // 读 ChatOptions 拿 temperature + maxTokens (Spring AI 1.1 公开 API)
        Double temperature = 0.1;  // 默认低温度, 让小模型 ReAct JSON 稳
        Integer maxTokens = 2048;
        ChatOptions opts = prompt.getOptions();
        if (opts != null) {
            if (opts.getTemperature() != null) temperature = opts.getTemperature();
            if (opts.getMaxTokens() != null) maxTokens = opts.getMaxTokens();
        }

        String content = glmService.chat(systemPrompt, userMsg.toString(), temperature, maxTokens);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
