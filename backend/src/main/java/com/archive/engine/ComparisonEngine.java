package com.archive.engine;

import com.archive.entity.ComparisonMethod;
import com.archive.entity.MaterialVersion;
import com.archive.repository.ComparisonMethodRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.service.AuditLogService;
import com.archive.service.LLMProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 对比引擎 — 对比两份报告并生成差异分析.
 *
 * <p>根据对比方法(ComparisonMethod)的 Prompt 模板和输出 Schema,
 * 调用 LLM 对比同一项目的两个材料版本(如立项报告 vs 申请报告),
 * 输出结构化的差异项列表。
 * 预置内置方法 DEFAULT_QA_VERIFY(Q&A 验证),业务方可通过管理界面自定义。
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonEngine {

    /** 默认对比方法代码 — Q&A 验证待落实问题. */
    public static final String DEFAULT_QA_VERIFY = "DEFAULT_QA_VERIFY";

    private final ComparisonMethodRepository comparisonMethodRepository;
    private final LLMProviderFactory llmProviderFactory;
    private final MaterialVersionRepository materialVersionRepository;
    private final AuditLogService auditLogService;

    /**
     * 对比同一项目下两个材料版本.
     *
     * @param projectId       项目 ID(仅用于审计日志)
     * @param fromVersionId   源版本 ID(如立项报告)
     * @param toVersionId     目标版本 ID(如申请报告)
     * @param methodCode      对比方法代码(不存在则回退 DEFAULT_QA_VERIFY)
     * @return 差异项列表,失败时返回空列表
     */
    @Async("taskExecutor")
    public List<Map<String, Object>> compare(Long projectId, Long fromVersionId,
                                              Long toVersionId, String methodCode) {
        try {
            // 1. 加载对比方法
            ComparisonMethod method = comparisonMethodRepository.findByCode(methodCode)
                    .orElseGet(() -> {
                        log.warn("Comparison method [{}] not found, fallback to [{}]",
                                methodCode, DEFAULT_QA_VERIFY);
                        return comparisonMethodRepository.findByCode(DEFAULT_QA_VERIFY).orElse(null);
                    });
            if (method == null) {
                log.warn("No comparison method available for projectId={}", projectId);
                return Collections.emptyList();
            }

            // 2. 读取源版本内容
            MaterialVersion fromMv = materialVersionRepository.findById(fromVersionId).orElse(null);
            if (fromMv == null) {
                log.warn("From version not found: id={}", fromVersionId);
                return Collections.emptyList();
            }
            String fromText = fromMv.getParsedText();
            if (fromText == null || fromText.isBlank()) {
                log.warn("From version parsed_text is empty: id={}", fromVersionId);
                return Collections.emptyList();
            }

            // 3. 读取目标版本内容
            MaterialVersion toMv = materialVersionRepository.findById(toVersionId).orElse(null);
            if (toMv == null) {
                log.warn("To version not found: id={}", toVersionId);
                return Collections.emptyList();
            }
            String toText = toMv.getParsedText();
            if (toText == null || toText.isBlank()) {
                log.warn("To version parsed_text is empty: id={}", toVersionId);
                return Collections.emptyList();
            }

            // 4. 构建 Prompt
            String prompt = method.getPromptTemplate()
                    .replace("${from_questions}", fromText)
                    .replace("${to_content}", toText);

            // 5. 调用 LLM
            String response = llmProviderFactory.chatJson(prompt, method.getOutputSchema());

            // 6. 解析结果
            List<Map<String, Object>> result = llmProviderFactory.parseJsonArrayResponse(response);

            // 7. 审计日志
            auditLogService.log("llm_call", "comparison", null,
                    "对比方法: " + methodCode + ", from=" + fromVersionId + ", to=" + toVersionId,
                    response);

            log.info("Comparison completed for projectId={}, method={}, items={}",
                    projectId, methodCode, result.size());

            return result;
        } catch (Exception e) {
            log.warn("Comparison failed for projectId={}, from={}, to={}, method={}: {}",
                    projectId, fromVersionId, toVersionId, methodCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 使用默认方法(DEFAULT_QA_VERIFY)对比两个版本.
     *
     * @param projectId     项目 ID
     * @param fromVersionId 源版本 ID
     * @param toVersionId   目标版本 ID
     * @return 差异项列表,失败时返回空列表
     */
    @Async("taskExecutor")
    public List<Map<String, Object>> compare(Long projectId, Long fromVersionId, Long toVersionId) {
        return compare(projectId, fromVersionId, toVersionId, DEFAULT_QA_VERIFY);
    }
}
