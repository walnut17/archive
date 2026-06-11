package com.archive.engine;

import com.archive.common.FailureType;
import com.archive.entity.ExtractionMethod;
import com.archive.entity.MaterialVersion;
import com.archive.repository.ExtractionMethodRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.service.AuditLogService;
import com.archive.provider.LLMProvider;
import com.archive.provider.LLMProviderFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Map;

/**
 * 字段抽取引擎 — 调用 LLM 从文档中提取结构化字段.
 *
 * <p>根据抽取方法(ExtractionMethod)的 Prompt 模板和输出 Schema,
 * 调用 LLM 从材料版本的解析文本中提取结构化数据。
 * 预置内置方法 DEFAULT_PROJECT_FIELDS,业务方可通过管理界面自定义。
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionEngine {

    /** 默认抽取方法代码 — 项目字段抽取. */
    public static final String DEFAULT_PROJECT_FIELDS = "DEFAULT_PROJECT_FIELDS";

    private final ExtractionMethodRepository extractionMethodRepository;
    private final LLMProviderFactory llmProviderFactory;
    private final AuditLogService auditLogService;
    private final MaterialVersionRepository materialVersionRepository;

    /**
     * 按指定方法抽取字段.
     *
     * @param materialVersionId 材料版本 ID
     * @param methodCode        抽取方法代码(若不存在则回退 DEFAULT_PROJECT_FIELDS)
     * @return 抽取结果 Map,失败时返回空 Map
     */
    @Async("taskExecutor")
    public Map<String, Object> extract(Long materialVersionId, String methodCode) {
        try {
            ExtractionMethod method = extractionMethodRepository.findByCode(methodCode)
                    .orElseGet(() -> {
                        log.warn("Extraction method [{}] not found, fallback to [{}]",
                                methodCode, DEFAULT_PROJECT_FIELDS);
                        return extractionMethodRepository.findByCode(DEFAULT_PROJECT_FIELDS).orElse(null);
                    });
            if (method == null) {
                log.warn("No extraction method available for materialVersionId={}", materialVersionId);
                return Collections.emptyMap();
            }

            MaterialVersion mv = materialVersionRepository.findById(materialVersionId).orElse(null);
            if (mv == null) {
                log.warn("MaterialVersion not found: id={}", materialVersionId);
                return Collections.emptyMap();
            }
            String materialContent = mv.getParsedText();
            if (materialContent == null || materialContent.isBlank()) {
                log.warn("MaterialVersion parsed_text is empty: id={}", materialVersionId);
                return Collections.emptyMap();
            }

            String title = mv.getOriginalFilename();
            String prompt = method.getPromptTemplate()
                    .replace("${material_title}", title != null ? title : "")
                    .replace("${material_content}", materialContent);

            LLMProvider provider = llmProviderFactory.getProvider();
            String response = provider.chat("你是文档字段抽取助手。按要求输出 JSON。", prompt);

            ObjectMapper mapper = new ObjectMapper();
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```\\s*$", "");
            }
            Map<String, Object> result = mapper.readValue(cleaned,
                    new TypeReference<Map<String, Object>>() {});

            auditLogService.logSimple("system", "LLM_CALL", "material_version", materialVersionId);

            log.info("Extraction completed for materialVersionId={}, method={}, fields={}",
                    materialVersionId, methodCode, result.size());

            return result;
        } catch (JsonProcessingException e) {
            onFailure(FailureType.PARSE_ERROR, e.getMessage());
            return Collections.emptyMap();
        } catch (RuntimeException e) {
            onFailure(classifyFailure(e), e.getMessage());
            return Collections.emptyMap();
        } catch (Exception e) {
            onFailure(classifyFailure(e), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * LLM 抽字段失败兜底 (RI-30).
     */
    public void onFailure(FailureType type, String message) {
        log.error("Extraction failed: type={}, msg={}", type, message);
    }

    private FailureType classifyFailure(Throwable e) {
        if (e instanceof SocketTimeoutException) {
            return FailureType.TIMEOUT;
        }
        if (e instanceof JsonProcessingException) {
            return FailureType.PARSE_ERROR;
        }
        if (e.getMessage() != null && e.getMessage().contains("API")) {
            return FailureType.API_ERROR;
        }
        Throwable cause = e.getCause();
        if (cause != null) {
            return classifyFailure(cause);
        }
        return FailureType.API_ERROR;
    }

    /**
     * 使用默认方法(DEFAULT_PROJECT_FIELDS)抽取字段.
     *
     * @param materialVersionId 材料版本 ID
     * @return 抽取结果 Map,失败时返回空 Map
     */
    @Async("taskExecutor")
    public Map<String, Object> extract(Long materialVersionId) {
        return extract(materialVersionId, DEFAULT_PROJECT_FIELDS);
    }

    /**
     * 读取材料版本解析文本.
     *
     * @param materialVersionId 材料版本 ID
     * @return 解析文本,不存在时返回 null
     */
    public String readMaterialContent(Long materialVersionId) {
        return materialVersionRepository.findById(materialVersionId)
                .map(MaterialVersion::getParsedText)
                .orElse(null);
    }
}
