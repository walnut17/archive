package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.entity.Project;
import com.archive.repository.ProjectRepository;
import com.archive.service.GlmService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FindProjectTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FindProjectTool.class);
    /** LLM 兜底阈值: 项目总数 < 此值才走 LLM, 避免项目多了 token 爆炸. */
    private static final long LLM_FALLBACK_MAX_TOTAL = 300;

    private final ProjectRepository projectRepo;
    private final GlmService glmService;

    @Override
    public String name() {
        return "find_project";
    }

    @Override
    public String description() {
        return "用语义从 project.name + customer_name 中找匹配的项目,返回 Top N 候选(带置信度)。"
             + "支持简称/拼音/口头语 (内部 4 级兜底: code 精确 → FULLTEXT → LIKE 模糊 → LLM 语义匹配).";
    }

    @Override
    public Class<?> argsClass() {
        return FindProjectArgs.class;
    }

    @Override
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        FindProjectArgs args = (FindProjectArgs) argsObj;
        String q = args.query == null ? "" : args.query.trim();
        int topN = args.topN == null || args.topN <= 0 ? 5 : args.topN;
        if (q.isEmpty()) return ToolResult.ok(List.of());

        // 1) 精确匹配 projectCode
        Optional<Project> exact = projectRepo.findByCode(q);
        if (exact.isPresent()) {
            ctx.setProjectCode(exact.get().getCode());
            return ToolResult.ok(List.of(new FindProjectMatch(
                    exact.get().getCode(),
                    exact.get().getName(),
                    exact.get().getCustomerName(),
                    1.0
            )));
        }

        // 2) FULLTEXT 模糊 (name / customer_name)
        List<Object[]> fulltextRows = projectRepo.searchByNameOrCustomerFulltext(q, topN);
        if (!fulltextRows.isEmpty()) {
            return finalizeMatches(fulltextRows, topN, ctx, "FULLTEXT");
        }

        // 3) LIKE 模糊兜底 (name / code / summary / customer_name 四字段)
        log.info("[find_project] FULLTEXT miss for q='{}', trying LIKE fallback", q);
        List<Project> likeRows = projectRepo.searchByKeywordAsList(q, PageRequest.of(0, topN));
        if (!likeRows.isEmpty()) {
            return finalizeFromEntities(likeRows, ctx, "LIKE");
        }

        // 4) LLM 兜底 (项目总数 < 300 才调, 避免 token 爆炸)
        long total = projectRepo.count();
        if (total > 0 && total <= LLM_FALLBACK_MAX_TOTAL) {
            log.info("[find_project] LIKE miss for q='{}' (total={}), trying LLM semantic fallback", q, total);
            List<Project> all = projectRepo.findAll();
            List<String> matchedCodes = glmService.semanticMatchProjects(q, all, topN);
            if (matchedCodes.isEmpty()) {
                return ToolResult.ok(List.of());
            }
            // 拉匹配到的项目实体
            Map<String, Project> byCode = all.stream()
                    .collect(Collectors.toMap(Project::getCode, p -> p, (a, b) -> a));
            List<Project> matched = matchedCodes.stream()
                    .map(byCode::get)
                    .filter(p -> p != null)
                    .toList();
            if (matched.isEmpty()) {
                return ToolResult.ok(List.of());
            }
            return finalizeFromEntities(matched, ctx, "LLM");
        }

        log.info("[find_project] No match for q='{}' (total={}, LLM fallback skipped)", q, total);
        return ToolResult.ok(List.of());
    }

    private ToolResult finalizeMatches(List<Object[]> rows, int topN, AgentContext ctx, String source) {
        double maxScore = ((Number) rows.get(0)[3]).doubleValue();
        List<FindProjectMatch> matches = new ArrayList<>();
        for (Object[] row : rows) {
            String code = (String) row[0];
            String name = (String) row[1];
            String customerName = (String) row[2];
            double score = ((Number) row[3]).doubleValue();
            double confidence = score / maxScore;
            matches.add(new FindProjectMatch(code, name, customerName, confidence));
        }
        FindProjectMatch top = matches.get(0);
        if (top.confidence >= 0.7) {
            ctx.setProjectCode(top.projectCode);
        }
        log.info("[find_project] {} match: q={}, top={} (conf={})", source, ctx.getQuestion(), top.projectCode, top.confidence);
        return ToolResult.ok(matches);
    }

    private ToolResult finalizeFromEntities(List<Project> projects, AgentContext ctx, String source) {
        List<FindProjectMatch> matches = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            // LIKE/LLM 命中没有原生 score, 用 1.0 / (i+1) 做伪 confidence
            double confidence = 1.0 / (i + 1);
            matches.add(new FindProjectMatch(p.getCode(), p.getName(), p.getCustomerName(), confidence));
        }
        FindProjectMatch top = matches.get(0);
        if (top.confidence >= 0.7) {
            ctx.setProjectCode(top.projectCode);
        }
        log.info("[find_project] {} match: q={}, top={} (conf={})", source, ctx.getQuestion(), top.projectCode, top.confidence);
        return ToolResult.ok(matches);
    }

    @Data
    public static class FindProjectArgs {
        @JsonProperty("query") String query;
        @JsonProperty("topN") Integer topN = 5;
    }

    @Data
    @AllArgsConstructor
    public static class FindProjectMatch {
        @JsonProperty("projectCode") private String projectCode;
        @JsonProperty("projectName") private String projectName;
        @JsonProperty("customerName") private String customerName;
        @JsonProperty("confidence") private double confidence;
    }
}
