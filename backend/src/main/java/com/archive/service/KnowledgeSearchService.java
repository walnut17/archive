package com.archive.service;

import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.entity.Project;
import com.archive.entity.Proposal;
import com.archive.repository.MaterialRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.repository.ProjectRepository;
import com.archive.repository.ProposalRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 知识库检索服务.
 *
 * 实现:
 *   1. MySQL 8.0 FULLTEXT + ngram 分词(主力,中文友好)
 *   2. 按相关性打分,返回 top N
 *   3. 关联查项目/议案/材料名(让结果可读)
 *   4. 切片:取解析文本的匹配片段(snippet),方便引用溯源
 *
 * 不向量化(不引 Qdrant / OpenSearch):
 *   - 用户原话 "50GB 文档,极致轻量,不上向量化"
 *   - 全文检索 + 关键词加权,90% 场景够用
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final ProjectRepository projectRepository;
    private final ProposalRepository proposalRepository;
    private final MaterialRepository materialRepository;
    private final MaterialVersionRepository materialVersionRepository;

    @PersistenceContext
    private EntityManager em;

    /**
     * 全文检索.
     *
     * @param question 用户问题
     * @param topN     返回前 N 条
     * @return 检索结果列表(按相关性倒序)
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(String question, int topN) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (topN < 1) topN = 10;
        if (topN > 50) topN = 50;

        // MySQL FULLTEXT MATCH AGAINST(BOOLEAN MODE 支持中文 ngram)
        // 关键词: 提取 question 的 token(ngram 不需要,MySQL 自动分词)
        // 简单做法:用 question 全文 + 转义特殊字符
        String escaped = question.replace("\"", "\\\"").replace("+", "\\+").replace("-", "\\-");

        // SQL:
        //   SELECT id, material_id, version_no, original_filename,
        //          MATCH(parsed_text) AGAINST(? IN BOOLEAN MODE) AS score,
        //          SUBSTRING(parsed_text, ?, ?) AS snippet
        //   FROM material_version
        //   WHERE parse_status = 'success' AND MATCH(parsed_text) AGAINST(? IN BOOLEAN MODE)
        //   ORDER BY score DESC
        //   LIMIT ?
        String sql = "SELECT v.id, v.material_id, v.version_no, v.original_filename, " +
                     "       v.parsed_text, " +
                     "       MATCH(v.parsed_text) AGAINST(:q IN BOOLEAN MODE) AS score " +
                     "FROM material_version v " +
                     "WHERE v.parse_status = 'success' " +
                     "  AND v.parsed_text IS NOT NULL " +
                     "  AND MATCH(v.parsed_text) AGAINST(:q IN BOOLEAN MODE) " +
                     "ORDER BY score DESC " +
                     "LIMIT :n";

        Query query = em.createNativeQuery(sql);
        query.setParameter("q", escaped);
        query.setParameter("n", topN);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            Long versionId = ((Number) row[0]).longValue();
            Long materialId = ((Number) row[1]).longValue();
            Integer versionNo = ((Number) row[2]).intValue();
            String filename = (String) row[3];
            String fullText = (String) row[4];
            Double score = row[5] == null ? 0.0 : ((Number) row[5]).doubleValue();

            // 关联查项目/议案/材料名
            Material material = materialRepository.findById(materialId).orElse(null);
            if (material == null) continue;
            Proposal proposal = proposalRepository.findById(material.getProposalId()).orElse(null);
            if (proposal == null) continue;
            Project project = projectRepository.findById(proposal.getProjectId()).orElse(null);

            // 切片:取第一个匹配关键词前后 100 字符作为 snippet
            String snippet = extractSnippet(fullText, question, 100);

            results.add(SearchResult.builder()
                    .versionId(versionId)
                    .materialId(materialId)
                    .materialTitle(material.getTitle())
                    .versionNo(versionNo)
                    .originalFilename(filename)
                    .projectCode(project != null ? project.getCode() : null)
                    .projectName(project != null ? project.getName() : null)
                    .proposalCode(proposal.getCode())
                    .proposalTitle(proposal.getTitle())
                    .snippet(snippet)
                    .fullText(fullText)
                    .score(score)
                    .build());
        }
        return results;
    }

    /**
     * 提取包含关键词的上下文片段.
     */
    private String extractSnippet(String text, String question, int contextLen) {
        if (text == null || text.isEmpty()) return "";
        // 找 question 的第一个非空字符在 text 中的位置
        for (int i = 0; i < question.length(); i++) {
            char c = question.charAt(i);
            if (Character.isLetterOrDigit(c) || isChinese(c)) {
                int pos = text.indexOf(c);
                if (pos >= 0) {
                    int start = Math.max(0, pos - contextLen);
                    int end = Math.min(text.length(), pos + contextLen);
                    String snippet = text.substring(start, end);
                    if (start > 0) snippet = "..." + snippet;
                    if (end < text.length()) snippet = snippet + "...";
                    return snippet;
                }
            }
        }
        // 没找到匹配,返回前 200 字
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    /**
     * 检索结果.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchResult {
        private Long versionId;
        private Long materialId;
        private String materialTitle;
        private Integer versionNo;
        private String originalFilename;
        private String projectCode;
        private String projectName;
        private String proposalCode;
        private String proposalTitle;
        /** 匹配上下文片段. */
        private String snippet;
        /** 完整文本(M2 重排可能用). */
        private String fullText;
        /** MySQL 相关性分数. */
        private Double score;
    }
}
