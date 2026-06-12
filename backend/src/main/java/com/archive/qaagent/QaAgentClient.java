package com.archive.qaagent;

import com.archive.agent.AgentResponse;
import com.archive.agent.AgentStep;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP 客户端 — 调用 Python qa-agent 微服务 (含 v1.2 流式 SSE).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.qa-agent.enabled", havingValue = "true", matchIfMissing = true)
public class QaAgentClient {

    private final QaAgentProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentResponse ask(String question, String sessionId) {
        try {
            PythonAskResponse resp;
            if (sessionId != null && !sessionId.isBlank()) {
                resp = post("/v1/turn/" + sessionId, Map.of("question", question), PythonAskResponse.class);
            } else {
                resp = post("/v1/ask", Map.of("question", question), PythonAskResponse.class);
            }
            return toAgentResponse(resp);
        } catch (RestClientException e) {
            log.warn("qa-agent ask failed: {}", e.getMessage());
            throw e;
        }
    }

    public ExtractionResult extractProjectFields(long materialVersionId) {
        try {
            return post("/v1/extract/project-fields",
                    Map.of("material_version_id", materialVersionId),
                    ExtractionResult.class);
        } catch (RestClientException e) {
            log.warn("qa-agent extract failed: {}", e.getMessage());
            ExtractionResult fail = new ExtractionResult();
            fail.setSuccess(false);
            fail.setFailureType("API_ERROR");
            fail.setMessage(e.getMessage());
            fail.setRetryable(true);
            return fail;
        }
    }

    public boolean isHealthy() {
        try {
            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.getTimeoutSeconds() * 1000);
            factory.setReadTimeout(5000);
            RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(factory)
                    .build()
                    .get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // v1.2: 流式 SSE 接口
    // =========================================================================

    /**
     * 流式问 (单轮) - 返回 Flux<StreamEvent> (前端逐字渲染).
     * <p>
     * 事件类型 4 种:
     * <ul>
     *   <li>step: ReAct 步完成 (含 tool/args/observation)
     *   <li>token: LLM 生成的 token
     *   <li>source: 来源命中 (PROJECT/MATERIAL/...)
     *   <li>done: 结束 (含 answer/tool_calls/agent_sources/...)
     * </ul>
     */
    public Flux<StreamEvent> streamAsk(String question, String sessionId) {
        String path;
        if (sessionId != null && !sessionId.isBlank()) {
            path = "/v1/turn/" + sessionId + "/stream";
        } else {
            path = "/v1/ask/stream";
        }
        return streamPost(path, Map.of("question", question));
    }

    /**
     * 流式 SSE POST: 解析 event:/data: 行, 转换为 StreamEvent 流.
     */
    private Flux<StreamEvent> streamPost(String path, Object body) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                ))
                .build();

        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(Mono.just(body), Map.class)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMapMany(line -> Flux.fromIterable(parseSseLine(line)))
                .onErrorResume(e -> {
                    log.warn("qa-agent stream error: {}", e.getMessage());
                    StreamEvent err = new StreamEvent();
                    err.setEvent("error");
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("message", e.getMessage() == null ? "stream failed" : e.getMessage());
                    err.setData(data);
                    return Flux.just(err);
                });
    }

    /**
     * 解析 1 行 SSE: "event: X" + "data: {...}" 视为同 1 个事件.
     * 简化: 我们采用单行格式 "event: X\ndata: {...}" 用 \n\n 分隔 (sse-starlette 默认).
     * Python 端 _sse_event 返回的格式是 "event: X\ndata: {...}\n\n".
     * <p>
     * WebClient.bodyToFlux(String.class) 按 chunk 切, 1 个 chunk 可能含多个事件.
     * 我们按 \n\n 切 chunk 后, 每段解析.
     */
    private List<StreamEvent> parseSseLine(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return Collections.emptyList();
        }
        java.util.List<StreamEvent> result = new java.util.ArrayList<>();
        // 多个事件可能粘在 1 个 chunk (chunk 没切 \n\n)
        String[] events = chunk.split("\n\n");
        for (String ev : events) {
            if (ev.isBlank()) continue;
            String eventName = null;
            String dataLine = null;
            for (String line : ev.split("\n")) {
                if (line.startsWith("event: ")) {
                    eventName = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    dataLine = line.substring(6).trim();
                }
            }
            if (eventName != null && dataLine != null) {
                try {
                    StreamEvent se = new StreamEvent();
                    se.setEvent(eventName);
                    se.setData(mapper.readValue(dataLine, Map.class));
                    result.add(se);
                } catch (Exception e) {
                    log.debug("SSE parse skip: {} - {}", dataLine, e.getMessage());
                }
            }
        }
        return result;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getTimeoutSeconds() * 1000);
        factory.setReadTimeout((int) properties.getTimeoutSeconds() * 1000);
        RestClient client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(type);
    }

    private AgentResponse toAgentResponse(PythonAskResponse resp) {
        AgentResponse ar = new AgentResponse();
        ar.setAnswer(resp.getAnswer());
        ar.setAgentMode(resp.isAgentMode());
        ar.setProjectSwitchHint(resp.getProjectSwitchHint());
        ar.setConfidenceBadge(resp.getConfidenceBadge());
        if (resp.getSteps() != null) {
            List<AgentStep> steps = resp.getSteps().stream().map(s -> {
                AgentStep step = new AgentStep();
                step.setIteration(s.getIteration());
                step.setThought(s.getThought());
                step.setTool(s.getTool());
                step.setToolArgs(s.getToolArgs());
                step.setObservation(s.getObservation());
                return step;
            }).toList();
            ar.setSteps(steps);
        } else {
            ar.setSteps(Collections.emptyList());
        }
        if (resp.getAgentSources() != null) {
            ar.setAgentSources(resp.getAgentSources().stream()
                    .map(obj -> mapper.convertValue(obj, com.archive.dto.Source.class))
                    .toList());
        }
        return ar;
    }

    @Data
    public static class PythonAskResponse {
        private String answer;
        @JsonProperty("agent_mode")
        private boolean agentMode = true;
        private List<PythonStep> steps;
        @JsonProperty("tool_calls")
        private Integer toolCalls;
        @JsonProperty("project_switch_hint")
        private String projectSwitchHint;
        @JsonProperty("confidence_badge")
        private String confidenceBadge;
        @JsonProperty("agent_sources")
        private List<Object> agentSources;
    }

    @Data
    public static class PythonStep {
        private int iteration;
        private String thought;
        private String tool;
        @JsonProperty("toolArgs")
        private String toolArgs;
        private String observation;
    }

    @Data
    public static class ExtractionResult {
        private boolean success;
        private Map<String, Object> data;
        @JsonProperty("failure_type")
        private String failureType;
        private String message;
        private Boolean retryable;
    }

    /**
     * v1.2: 流式事件 DTO.
     */
    @Data
    public static class StreamEvent {
        /** event: 事件名 (step / token / source / done / error) */
        private String event;
        /** data: 事件数据 (Map) */
        private Map<String, Object> data;
    }
}
