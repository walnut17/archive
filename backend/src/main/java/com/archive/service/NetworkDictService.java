package com.archive.service;

import com.archive.entity.DictItem;
import com.archive.repository.DictItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网络字典查询 — 百度百科 + 维基百科 (RI-26, PM D-2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkDictService {

    private static final String DICT_TYPE = "network_dict_source";
    private static final Set<String> V11_SOURCES = Set.of("baidu_baike", "wikipedia_zh", "wiki", "wikipedia-zh");
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private final DictItemRepository dictItemRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public DictLookupResult lookup(String query, String preferredSource) {
        if (query == null || query.isBlank()) {
            return DictLookupResult.notFound("EMPTY_QUERY");
        }

        // Layer 1: load configured sources
        List<DictItem> sources = dictItemRepo.findByTypeCodeAndEnabledOrderBySortOrderAsc(DICT_TYPE, true);
        sources = sources.stream()
                .filter(s -> V11_SOURCES.contains(normalizeSourceCode(s.getItemKey())))
                .collect(Collectors.toList());

        if (preferredSource != null && !preferredSource.isBlank()) {
            String normalized = normalizeSourceCode(preferredSource);
            List<DictItem> filtered = sources.stream()
                    .filter(s -> normalizeSourceCode(s.getItemKey()).equals(normalized))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                return DictLookupResult.notFound("PREFERRED_SOURCE_NOT_FOUND");
            }
            sources = filtered;
        }

        if (sources.isEmpty()) {
            return DictLookupResult.notFound("NO_SOURCE_ENABLED");
        }

        // Layers 2-5: try each source (API error / empty / timeout → next)
        for (DictItem source : sources) {
            try {
                String definition = callApi(source, query.trim());
                if (definition != null && !definition.isBlank()) {
                    return DictLookupResult.found(definition, source.getItemKey());
                }
                log.warn("dict source {} returned empty for query={}", source.getItemKey(), query);
            } catch (java.net.http.HttpTimeoutException e) {
                log.warn("dict source {} timeout: {}", source.getItemKey(), e.getMessage());
            } catch (Exception e) {
                log.warn("dict source {} failed: {}", source.getItemKey(), e.getMessage());
            }
        }

        // Layer 6: all sources exhausted
        return DictLookupResult.notFound("INTRANET_BLOCKED");
    }

    private String normalizeSourceCode(String code) {
        if (code == null) return "";
        return code.trim().toLowerCase().replace('-', '_');
    }

    private String callApi(DictItem source, String query) throws Exception {
        JsonNode config = parseConfig(source.getItemValue());
        String baseUrl = config.path("baseUrl").asText(null);
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = config.path("url").asText(null);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }

        int timeoutMs = config.has("timeout") ? config.get("timeout").asInt(DEFAULT_TIMEOUT_MS) : DEFAULT_TIMEOUT_MS;
        String apiKey = config.path("apiKey").asText("");
        String sourceCode = normalizeSourceCode(source.getItemKey());

        String requestUrl = buildRequestUrl(sourceCode, baseUrl, query, apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        return extractDefinition(sourceCode, response.body());
    }

    private JsonNode parseConfig(String itemValue) {
        if (itemValue == null || itemValue.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            if (itemValue.trim().startsWith("{")) {
                return objectMapper.readTree(itemValue);
            }
        } catch (Exception e) {
            log.warn("Invalid dict_item JSON config: {}", e.getMessage());
        }
        return objectMapper.createObjectNode().put("baseUrl", itemValue);
    }

    private String buildRequestUrl(String sourceCode, String baseUrl, String query, String apiKey) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        if (sourceCode.contains("baidu")) {
            String sep = baseUrl.contains("?") ? "&" : "?";
            String url = baseUrl + sep + "search=" + encoded;
            if (apiKey != null && !apiKey.isBlank()) {
                url += "&apiKey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            }
            return url;
        }
        if (sourceCode.contains("wiki")) {
            String sep = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + sep + "action=opensearch&search=" + encoded + "&format=json";
        }
        String sep = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + sep + "query=" + encoded;
    }

    private String extractDefinition(String sourceCode, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        if (sourceCode.contains("wiki")) {
            try {
                JsonNode arr = objectMapper.readTree(body);
                if (arr.isArray() && arr.size() >= 2 && arr.get(1).isArray() && arr.get(1).size() > 0) {
                    return arr.get(1).get(0).asText();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String summary = root.path("summary").asText(null);
            if (summary != null && !summary.isBlank()) return summary;
            String desc = root.path("description").asText(null);
            if (desc != null && !desc.isBlank()) return desc;
            String lemma = root.path("lemmaDesc").asText(null);
            if (lemma != null && !lemma.isBlank()) return lemma;
        } catch (Exception ignored) {
            // plain text fallback
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    @Data
    @Builder
    public static class DictLookupResult {
        private boolean found;
        private String definition;
        private String source;
        private String reason;

        public static DictLookupResult found(String definition, String source) {
            return DictLookupResult.builder()
                    .found(true)
                    .definition(definition)
                    .source(source)
                    .build();
        }

        public static DictLookupResult notFound(String reason) {
            return DictLookupResult.builder()
                    .found(false)
                    .reason(reason)
                    .build();
        }
    }
}
