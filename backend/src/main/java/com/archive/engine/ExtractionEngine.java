package com.archive.engine;

import com.archive.entity.ExtractionMethod;
import com.archive.entity.MaterialVersion;
import com.archive.repository.ExtractionMethodRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.service.AuditLogService;
import com.archive.service.LLMProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
            // 1. 加载抽取方法
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

            // 2. 读取材料内容
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

            // 3. 构建 Prompt
            String title = mv.getOriginalFilename();
            String prompt = method.getPromptTemplate()
                    .replace("${material_title}", title != null ? title : "")
                    .replace("${material_content}", materialContent);

            // 4. 调用 LLM
            String response = llmProviderFactory.chatJson(prompt, method.getOutputSchema());

            // 5. 解析结果
            Map<String, Object> result = llmProviderFactory.parseJsonResponse(response);

            // 6. 审计日志
            auditLogService.log("llm_call", "material_version", materialVersionId,
                    "抽取方法: " + methodCode, response);

            log.info("Extraction completed for materialVersionId={}, method={}, fields={}",
                    materialVersionId, methodCode, result.size());

            return result;
        } catch (Exception e) {
            log.warn("Extraction failed for materialVersionId={}, method={}: {}",
                    materialVersionId, methodCode, e.getMessage());
            return Collections.emptyMap();
        }
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
