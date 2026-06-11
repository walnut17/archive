package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.common.SwitchDecision;
import com.archive.entity.Project;
import com.archive.repository.ProjectRepository;
import com.archive.service.GlmService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class FindProjectTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FindProjectTool.class);
    private static final Pattern LATIN_OR_DIGIT_TOKEN = Pattern.compile("[a-zA-Z0-9]{2,}");

    private final ProjectRepository projectRepo;
    private final GlmService glmService;

    /** LLM 兜底阈值: 项目总数 < 此值才走 LLM, 避免项目多了 token 爆炸. 可配 (默认 300). */
    @Value("${spring.ai.agent.find-project.llm-fallback-max-total:300}")
    private long llmFallbackMaxTotal = 300;

    public FindProjectTool(ProjectRepository projectRepo, GlmService glmService) {
        this.projectRepo = projectRepo;
        this.glmService = glmService;
    }

    @Override
    public String name() {
        return "find_project";
    }

    @Override
    public String description() {
        return "用语义从 project.name + customer_name 中找匹配的项目,返回 Top N 候选(带置信度)。"
             + "支持简称/拼音/口头语 (内部 4 级兜底: code 精确 → FULLTEXT → LIKE 模糊 → LLM 语义匹配)."
             + " v1.1 内部 5 级隐式切换判定 (不算 ReAct 步数).";
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
        if (q.isEmpty()) {
            return ToolResult.ok(List.of());
        }

        List<String> variants = buildSearchVariants(q);
        log.info("[find_project] query='{}', search variants={}", q, variants);

        for (String variant : variants) {
            Optional<ToolResult> dbHit = tryDbMatch(variant, topN, ctx);
            if (dbHit.isPresent()) {
                return dbHit.get();
            }
        }

        return tryLlmFallback(q, topN, ctx);
    }

    /**
     * 从用户口头语生成多组 MySQL 检索词 (单次 tool 调用内全部尝试, 避免 Agent 死循环重试).
     * 例: "lmz项目" → ["lmz项目", "lmz"]
     */
    static List<String> buildSearchVariants(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String q = raw.trim();
        variants.add(q);

        String noSuffix = q.replaceAll("(项目|工程|案件|业务|计划|那个|这笔)$", "").trim();
        if (!noSuffix.isEmpty()) {
            variants.add(noSuffix);
        }

        Matcher matcher = LATIN_OR_DIGIT_TOKEN.matcher(q);
        if (!looksLikeProjectCode(q)) {
            while (matcher.find()) {
                String token = matcher.group();
                if (!token.chars().allMatch(Character::isDigit)) {
                    variants.add(token);
                }
            }
        }

        return new ArrayList<>(variants);
    }

    private static boolean looksLikeProjectCode(String q) {
        return q.matches("(?i)PRJ[-_\\s]?\\d.*");
    }

    private Optional<ToolResult> tryDbMatch(String variant, int topN, AgentContext ctx) {
        Optional<Project> exact = projectRepo.findByCode(variant);
        if (exact.isPresent()) {
            FindProjectMatch match = new FindProjectMatch(
                    exact.get().getCode(),
                    exact.get().getName(),
                    exact.get().getCustomerName(),
                    1.0
            );
            return Optional.of(finalizeWithSwitchRule(List.of(match), ctx, "EXACT"));
        }

        List<Object[]> fulltextRows = List.of();
        try {
            fulltextRows = projectRepo.searchByNameOrCustomerFulltext(variant, topN);
        } catch (Exception ex) {
            log.debug("[find_project] FULLTEXT unavailable for variant='{}': {}", variant, ex.getMessage());
        }
        if (!fulltextRows.isEmpty()) {
            return Optional.of(finalizeMatches(fulltextRows, ctx, "FULLTEXT"));
        }

        List<Project> likeRows = projectRepo.searchByKeywordAsList(variant, PageRequest.of(0, topN));
        if (!likeRows.isEmpty()) {
            log.info("[find_project] LIKE hit for variant='{}', count={}", variant, likeRows.size());
            return Optional.of(finalizeFromEntities(likeRows, ctx, "LIKE"));
        }

        return Optional.empty();
    }

    private ToolResult tryLlmFallback(String originalQuery, int topN, AgentContext ctx) {
        long total = projectRepo.count();
        if (total <= 0 || total > llmFallbackMaxTotal) {
            log.info("[find_project] No match for q='{}' (total={}, LLM fallback skipped)", originalQuery, total);
            return ToolResult.ok(List.of());
        }

        log.info("[find_project] DB miss for all variants of q='{}' (total={}), trying LLM semantic fallback", originalQuery, total);
        List<Project> all = projectRepo.findAll();
        List<String> matchedCodes = glmService.semanticMatchProjects(originalQuery, all, topN);
        if (matchedCodes.isEmpty()) {
            return ToolResult.ok(List.of());
        }

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

    private ToolResult finalizeMatches(List<Object[]> rows, AgentContext ctx, String source) {
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
        return finalizeWithSwitchRule(matches, ctx, source);
    }

    private ToolResult finalizeFromEntities(List<Project> projects, AgentContext ctx, String source) {
        List<FindProjectMatch> matches = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            double confidence = 1.0 / (i + 1);
            matches.add(new FindProjectMatch(p.getCode(), p.getName(), p.getCustomerName(), confidence));
        }
        return finalizeWithSwitchRule(matches, ctx, source);
    }

    /**
     * 5 级隐式切换判定 (RI-23) — in-tool, 不算 ReAct 步数.
     */
    private ToolResult finalizeWithSwitchRule(List<FindProjectMatch> matches, AgentContext ctx, String source) {
        if (matches.isEmpty()) {
            return ToolResult.ok(matches);
        }

        FindProjectMatch top = matches.get(0);
        String currentLock = ctx.getLockedProjectCode();
        SwitchDecision decision = applyImplicitSwitchRule(top, currentLock);
        ctx.setLastSwitchDecision(decision);

        switch (decision) {
            case SAME_CONFIRMED -> ctx.setProjectCode(top.projectCode);
            case SAME_PROBABLY, DIFFERENT_PROBABLY -> {
                if (currentLock == null || currentLock.isBlank()) {
                    ctx.setProjectCode(top.projectCode);
                }
            }
            case UNCLEAR -> {
                if (currentLock == null || currentLock.isBlank()) {
                    if (top.confidence >= 0.7) {
                        ctx.setProjectCode(top.projectCode);
                    }
                }
            }
        }

        log.info("[find_project] {} match: q={}, top={} (conf={}), switch={}",
                source, ctx.getQuestion(), top.projectCode, top.confidence, decision);
        return ToolResult.ok(buildResult(matches, decision));
    }

    /**
     * 5 级隐式项目切换判定规则 (RI-23).
     */
    static SwitchDecision applyImplicitSwitchRule(FindProjectMatch top, String currentLock) {
        double conf = top.confidence;
        boolean sameCode = currentLock != null && top.projectCode.equals(currentLock);

        if (currentLock == null || currentLock.isBlank()) {
            if (conf >= 0.95) return SwitchDecision.SAME_CONFIRMED;
            if (conf >= 0.7) return SwitchDecision.SAME_PROBABLY;
            if (conf >= 0.5) return SwitchDecision.UNCLEAR;
            return SwitchDecision.UNCLEAR;
        }

        if (sameCode && conf >= 0.95) return SwitchDecision.SAME_CONFIRMED;
        if (sameCode && conf >= 0.7) return SwitchDecision.SAME_PROBABLY;
        if (sameCode && conf >= 0.5) return SwitchDecision.UNCLEAR;
        if (!sameCode && conf >= 0.7) return SwitchDecision.DIFFERENT_PROBABLY;
        return SwitchDecision.UNCLEAR;
    }

    private List<FindProjectMatch> buildResult(List<FindProjectMatch> matches, SwitchDecision decision) {
        for (FindProjectMatch match : matches) {
            match.switchDecision = decision.name();
        }
        return matches;
    }

    @Data
    public static class FindProjectArgs {
        @JsonProperty("query") String query;
        @JsonProperty("topN") Integer topN = 5;
    }

    @Data
    public static class FindProjectMatch {
        @JsonProperty("projectCode") private String projectCode;
        @JsonProperty("projectName") private String projectName;
        @JsonProperty("customerName") private String customerName;
        @JsonProperty("confidence") private double confidence;
        @JsonProperty("switchDecision") private String switchDecision;

        public FindProjectMatch(String projectCode, String projectName, String customerName, double confidence) {
            this.projectCode = projectCode;
            this.projectName = projectName;
            this.customerName = customerName;
            this.confidence = confidence;
        }
    }
}
