package com.archive.provider;

import com.archive.service.GlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GLM 智谱 Provider — 包装 {@link GlmService}，并提供 vision 多模态支持。
 * <p>
 * chat/chatJson 委托给 GlmService，vision 直接调用 GLM-4V API。
 *
 * @author Mavis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GLMProvider implements LLMProvider {

    private final GlmService glmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.glm.api-key:}")
    private String apiKey;

    @Value("${app.glm.chat-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String chatUrl;

    @Value("${app.glm.chat-model:glm-4v-flash}")
    private String visionModel;

    @Value("${app.glm.timeout-seconds:60}")
    private int timeoutSeconds;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            return glmService.chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("GLM chat failed", e);
            throw new RuntimeException("GLM chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> type) {
        try {
            String text = glmService.chat(systemPrompt, userPrompt);
            return objectMapper.readValue(text, type);
        } catch (Exception e) {
            log.error("GLM chatJson failed", e);
            throw new RuntimeException("GLM chatJson failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String vision(String prompt, byte[] imageBytes, String mimeType) {
        checkApiKey();
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            Map<String, Object> body = new HashMap<>();
            body.put("model", visionModel);

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();
            // 文本部分
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            content.add(textPart);
            // 图片部分
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", dataUrl));
            content.add(imagePart);

            userMessage.put("content", content);
            messages.add(userMessage);
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("max_tokens", 2048);

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("GLM vision API error {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("GLM vision API error: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new RuntimeException("GLM vision response empty");
        } catch (Exception e) {
            log.error("GLM vision failed", e);
            throw new RuntimeException("GLM vision failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "glm";
    }

    private void checkApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GLM API key 未配置");
        }
    }
}
