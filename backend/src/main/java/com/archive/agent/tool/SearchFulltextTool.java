package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.service.KnowledgeSearchService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SearchFulltextTool implements AgentTool {

    private final KnowledgeSearchService searchService;

    @Override
    public String name() {
        return "search_fulltext";
    }

    @Override
    public String description() {
        return "Search in material full-text content, returns top N results with project/proposal/material metadata";
    }

    @Override
    public Class<?> argsClass() {
        return SearchArgs.class;
    }

    @Override
    public ToolResult execute(Object args, AgentContext ctx) {
        SearchArgs searchArgs = (SearchArgs) args;
        List<KnowledgeSearchService.SearchResult> results = searchService.search(searchArgs.query, searchArgs.topN);
        String projectCode = ctx.getProjectCode();
        if (projectCode != null && !projectCode.isBlank()) {
            results = results.stream()
                    .filter(r -> projectCode.equals(r.getProjectCode()))
                    .collect(Collectors.toList());
        }
        return ToolResult.ok(results);
    }

    @Data
    public static class SearchArgs {
        @JsonProperty("query") String query;
        @JsonProperty("topN") Integer topN = 10;
        @JsonProperty("projectCode") String projectCode;
    }
}
