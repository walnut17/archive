package com.archive.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Provider — 对接任何 OpenAI 兼容 API（如 OpenAI、Azure OpenAI、DeepSeek 等）。
 * <p>
 * 通过 {@code app.llm.provider=openai} 激活。
 * 配置项：{@code app.llm.openai.*}。
 *
 * @author Mavis
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai")
public class OpenAIProvider implements LLMProvider {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.llm.openai.api-key:}")
    private String apiKey;

    @Value("${app.llm.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${app.llm.openai.chat-model:gpt-4o-mini}")
    private String chatModel;

    @Value("${app.llm.openai.vision-model:gpt-4o-mini}")
    private String visionModel;

    @Value("${app.llm.openai.timeout-seconds:60}")
    private int timeoutSeconds;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = buildChatRequest(systemPrompt, userPrompt, chatModel, false);
            String json = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = buildHeaders();
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/v1/chat/completions", entity, String.class);

            return extractContent(response.getBody());
        } catch (Exception e) {
            log.error("OpenAI chat failed", e);
            throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> type) {
        try {
            Map<String, Object> requestBody = buildChatRequest(systemPrompt, userPrompt, chatModel, true);
            String json = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = buildHeaders();
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/v1/chat/completions", entity, String.class);

            String content = extractContent(response.getBody());
            return objectMapper.readValue(content, type);
        } catch (Exception e) {
            log.error("OpenAI chatJson failed", e);
            throw new RuntimeException("OpenAI chatJson failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String vision(String prompt, byte[] imageBytes, String mimeType) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建多模态消息
            Map<String, Object> systemMessage = Map.of("role", "system", "content", prompt);

            Map<String, Object> userContent = new HashMap<>();
            userContent.put("role", "user");

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", "data:" + mimeType + ";base64," + base64Image));

            userContent.put("content", List.of(textPart, imagePart));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", visionModel);
            requestBody.put("messages", List.of(systemMessage, userContent));
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 2048);

            String json = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = buildHeaders();
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/v1/chat/completions", entity, String.class);

            return extractContent(response.getBody());
        } catch (Exception e) {
            log.error("OpenAI vision failed", e);
            throw new RuntimeException("OpenAI vision failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "openai";
    }

    // ==================== 内部工具方法 ====================

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> buildChatRequest(String systemPrompt, String userPrompt,
                                                  String model, boolean jsonMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2048);

        if (jsonMode) {
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            body.put("response_format", responseFormat);
        }

        return body;
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new RuntimeException("OpenAI response empty");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }
}
