package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.service.NetworkDictService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具 7: 网络字典查询 (RI-26).
 */
@Component
@RequiredArgsConstructor
public class NetworkDictLookupTool implements AgentTool {

    private final NetworkDictService dictService;

    @Override
    public String name() {
        return "network_dict_lookup";
    }

    @Override
    public String description() {
        return "查询网络字典(百度百科/维基百科), 返回术语中文定义. 查不到不抛异常, 返回 found=false.";
    }

    @Override
    public Class<?> argsClass() {
        return NetworkDictLookupArgs.class;
    }

    @Override
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        NetworkDictLookupArgs args = (NetworkDictLookupArgs) argsObj;
        String query = args.query == null ? "" : args.query.trim();
        if (query.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("found", false);
            empty.put("definition", null);
            empty.put("source", null);
            empty.put("reason", "EMPTY_QUERY");
            return ToolResult.ok(empty);
        }

        NetworkDictService.DictLookupResult result = dictService.lookup(query, args.source);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("found", result.isFound());
        payload.put("definition", result.getDefinition());
        payload.put("source", result.getSource());
        payload.put("reason", result.isFound() ? null : result.getReason());
        return ToolResult.ok(payload);
    }

    @Data
    public static class NetworkDictLookupArgs {
        @JsonProperty("query") private String query;
        @JsonProperty("source") private String source;
    }
}
