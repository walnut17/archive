package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.entity.Project;
import com.archive.repository.ProjectRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FindProjectTool implements AgentTool {

    private final ProjectRepository projectRepo;

    @Override
    public String name() {
        return "find_project";
    }

    @Override
    public String description() {
        return "用语义从 project.name + customer_name 中找匹配的项目,返回 Top N 候选(带置信度)。";
    }

    @Override
    public Class<?> argsClass() {
        return FindProjectArgs.class;
    }

    @Override
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        FindProjectArgs args = (FindProjectArgs) argsObj;

        // 1) exact match by projectCode
        Optional<Project> exact = projectRepo.findByCode(args.query);
        if (exact.isPresent()) {
            ctx.setProjectCode(exact.get().getCode());
            return ToolResult.ok(List.of(new FindProjectMatch(
                    exact.get().getCode(),
                    exact.get().getName(),
                    exact.get().getCustomerName(),
                    1.0
            )));
        }

        // 2) FULLTEXT fuzzy match
        List<Object[]> rows = projectRepo.searchByNameOrCustomerFulltext(args.query, args.topN);
        if (rows.isEmpty()) {
            return ToolResult.ok(List.of());
        }

        // 3) calculate confidence scores relative to max score
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

        // 4) lock highest confidence project (>= 0.7)
        FindProjectMatch top = matches.get(0);
        if (top.confidence >= 0.7) {
            ctx.setProjectCode(top.projectCode);
        }

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
