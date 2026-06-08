package com.archive.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock Provider — 返回固定数据，用于开发/测试时无需真实 API 调用。
 * <p>
 * 通过 {@code app.llm.provider=mock} 激活。
 *
 * @author Mavis
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "mock")
public class MockProvider implements LLMProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.info("[MockProvider] chat() called — systemPrompt={}, userPrompt={}", systemPrompt, userPrompt);
        return "这是一个模拟回复。测试消息: " + userPrompt;
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> type) {
        log.info("[MockProvider] chatJson() called — systemPrompt={}, userPrompt={}, type={}",
                systemPrompt, userPrompt, type.getSimpleName());
        try {
            return objectMapper.readValue("{}", type);
        } catch (Exception e) {
            throw new RuntimeException("MockProvider chatJson failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String vision(String prompt, byte[] imageBytes, String mimeType) {
        log.info("[MockProvider] vision() called — prompt={}, mimeType={}, imageSize={}",
                prompt, mimeType, imageBytes.length);
        return "模拟 OCR 结果: [图片包含文本内容]";
    }

    @Override
    public String name() {
        return "mock";
    }
}
