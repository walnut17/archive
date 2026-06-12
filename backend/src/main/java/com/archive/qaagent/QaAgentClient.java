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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP 客户端 — 调用 Python qa-agent 微服务.
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
        // agentSources 映射（Java 侧 Source 结构，由 Python 返回的 agent_sources 透传）
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
}
