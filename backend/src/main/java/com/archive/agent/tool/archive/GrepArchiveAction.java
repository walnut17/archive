package com.archive.agent.tool.archive;

import com.archive.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在文本文件中按行搜索关键词（grep）.
 * 只支持 substring 匹配，不支持正则（防止 ReDoS）.
 */
@Component
public class GrepArchiveAction implements ArchiveFsAction {

    private static final Logger log = LoggerFactory.getLogger(GrepArchiveAction.class);

    @Override
    public String actionName() {
        return "grep";
    }

    @Override
    public ToolResult execute(ArchiveFsContext ctx) {
        if (ctx.relativePath() == null || ctx.pattern() == null) {
            return ToolResult.error("grep 需要 relativePath 和 pattern 参数");
        }
        if (ctx.pattern().length() > 200) {
            return ToolResult.error("pattern 长度不能超过 200");
        }

        try {
            Path file = ctx.guard().resolve(ctx.zone(), ctx.relativePath());
            if (!Files.isRegularFile(file) || !isTextFile(file)) {
                return ToolResult.error("路径不是文本文件或无法读取: " + file);
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<MatchLine> matches = new ArrayList<>();
            int maxLines = ctx.maxLines();

            for (int i = 0; i < lines.size() && matches.size() < maxLines; i++) {
                if (lines.get(i).contains(ctx.pattern())) {
                    matches.add(new MatchLine(i + 1, lines.get(i).trim()));
                }
            }

            boolean truncated = matches.size() >= maxLines;

            return ToolResult.ok(Map.of(
                    "action", "grep",
                    "zone", ctx.zone(),
                    "path", ctx.relativePath(),
                    "matches", matches,
                    "totalMatches", matches.size(),
                    "truncated", truncated
            ));
        } catch (IOException e) {
            log.warn("[GrepArchiveAction] 读取失败: {}", e.getMessage());
            return ToolResult.error("读取文件失败: " + e.getMessage());
        }
    }

    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")
                || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".html");
    }

    public record MatchLine(int lineNo, String text) {}
}
