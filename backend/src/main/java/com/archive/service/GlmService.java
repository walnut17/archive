package com.archive.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.archive.common.LlmScenario;
import com.archive.entity.LlmCallLog;
import com.archive.repository.LlmCallLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 GLM 客户端.
 *
 * 支持:
 *   1. GLM-4-Flash 文本问答(免费,智能重排 + 摘要)
 *   2. GLM-4V 视觉问答(扫描件 OCR,M5 用)
 *
 * 兜底:
 *   - apiKey 为空 → 抛 "未配置",调用方决定降级
 *   - 网络失败 → 抛异常,调用方降级
 *
 * @author Mavis
 */
@Slf4j
@Service
public class GlmService {

    @Value("${app.glm.api-key:}")
    private String apiKey;

    @Value("${app.glm.chat-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String chatUrl;

    @Value("${app.glm.chat-model:glm-4-flash}")
    private String chatModel;

    @Value("${app.glm.timeout-seconds:60}")
    private int timeoutSeconds;

    /**
     * 埋点仓库(用 setter 注入,不破坏现有构造).
     */
    @Autowired
    private LlmCallLogRepository llmCallLogRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 埋点包装:任何 LLM 调用都通过它,记录用户/场景/token/耗时/状态.
     */
    private <T> T callWithLog(LlmScenario scenario, java.util.function.Supplier<T> call) {
        long start = System.currentTimeMillis();
        String username = currentUsername();
        try {
            T result = call.get();
            saveLog(scenario, username, "SUCCESS", (int) (System.currentTimeMillis() - start), null);
            return result;
        } catch (RuntimeException e) {
            saveLog(scenario, username, "FAILED", (int) (System.currentTimeMillis() - start), e.getMessage());
            throw e;
        } catch (Exception e) {
            saveLog(scenario, username, "FAILED", (int) (System.currentTimeMillis() - start), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 写埋点日志.本期只统计次数,token 字段暂不写(智谱免费).
     * @param scenario 场景
     * @param username 调用人(未登录为 null)
     * @param status SUCCESS / FAILED
     * @param durationMs 耗时
     * @param error 失败原因(成功时 null)
     */
    private void saveLog(LlmScenario scenario, String username, String status,
                        int durationMs, String error) {
        try {
            LlmCallLog log = LlmCallLog.builder()
                    .username(username)
                    .scenario(scenario.name())
                    .model(chatModel)
                    .durationMs(durationMs)
                    .status(status)
                    .errorMessage(error != null && error.length() > 500
                            ? error.substring(0, 500) : error)
                    // token 字段本期不统计,留 NULL
                    .build();
            llmCallLogRepository.save(log);
        } catch (Exception e) {
            // 埋点失败不能影响业务路径
            log.warn("LlmCallLog save failed (non-fatal): {}", e.getMessage());
        }
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            return auth.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通用文本问答.
     */
    public String chat(String systemPrompt, String userPrompt) {
        return callWithLog(LlmScenario.QA, () -> doChat(systemPrompt, userPrompt, null, null));
    }

    /**
     * 带可选参数 (temperature / maxTokens) 的 chat 重载.
     * null 表示用 GlmService 默认值.
     */
    public String chat(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens) {
        return callWithLog(LlmScenario.QA, () -> doChat(systemPrompt, userPrompt, temperature, maxTokens));
    }

    private String doChat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, null, null);
    }

    private String doChat(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens) {
        checkApiKey();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", chatModel);
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);
            // 可选参数 (Mavis 加): 调低 temperature 让小模型 JSON 输出更稳
            if (temperature != null) body.put("temperature", temperature);
            if (maxTokens != null) body.put("max_tokens", maxTokens);
            // 注: 之前这里有硬编码 body.put("temperature", 0.3) + body.put("max_tokens", 2048) 覆盖
            // 参数, 删了 (P0-19 修)

            String json = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("GLM API error {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("GLM API error: " + response.statusCode());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new RuntimeException("GLM response empty");
        } catch (Exception e) {
            log.error("GLM chat failed", e);
            throw new RuntimeException("GLM call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 智能重排 — 拿 top N 个搜索结果,让 LLM 按问题相关性重新排序.
     *
     * @param question 用户问题
     * @param candidates 候选结果(每条带 snippet)
     * @return 重排后的 top 列表(LLM 返回 JSON 数组)
     */
    public String rerank(String question, List<KnowledgeSearchService.SearchResult> candidates) {
        return callWithLog(LlmScenario.RERANK, () -> doRerank(question, candidates));
    }

    private String doRerank(String question, List<KnowledgeSearchService.SearchResult> candidates) {
        checkApiKey();
        if (candidates == null || candidates.isEmpty()) {
            return "[]";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题:").append(question).append("\n\n");
        prompt.append("以下是候选文档片段(已按 MySQL 全文检索打分排序),请你按\"和用户问题的相关度\"重新排序:\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            KnowledgeSearchService.SearchResult r = candidates.get(i);
            prompt.append("[").append(i + 1).append("] ");
            prompt.append("项目:").append(safe(r.getProjectName())).append(" | ");
            prompt.append("议案:").append(safe(r.getProposalTitle())).append(" | ");
            prompt.append("材料:").append(safe(r.getMaterialTitle())).append(" v").append(r.getVersionNo()).append("\n");
            prompt.append("片段:").append(safe(r.getSnippet())).append("\n\n");
        }
        prompt.append("请返回一个 JSON 数组,格式:[1, 3, 2, 4, ...](按相关度从高到低,引用上面的序号)。");
        prompt.append("只返回 JSON,不要解释。");

        String system = "你是一个严谨的档案管理助手,负责从候选材料中找出和用户问题最相关的几条。";

        try {
            String resp = chat(system, prompt.toString());
            // 简单提取 JSON 数组
            int start = resp.indexOf('[');
            int end = resp.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return resp.substring(start, end + 1);
            }
            return "[]";
        } catch (Exception e) {
            log.warn("Rerank failed, fall back to original order", e);
            // 兜底:返回原顺序
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < candidates.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(i + 1);
            }
            sb.append("]");
            return sb.toString();
        }
    }

    private void checkApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "智谱 API key 未配置 (config.json 的 glm.apiKey),M2 知识库问答 / M5 扫描件 OCR 需要配置");
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\n", " ").trim();
    }
}
