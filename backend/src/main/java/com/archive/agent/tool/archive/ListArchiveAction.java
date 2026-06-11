package com.archive.agent.tool.archive;

import com.archive.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列出目录内容（ls）.
 * 非递归，仅返回文件名、大小、修改时间.
 */
@Component
public class ListArchiveAction implements ArchiveFsAction {

    private static final Logger log = LoggerFactory.getLogger(ListArchiveAction.class);
    private static final int MAX_ENTRIES = 100;

    @Override
    public String actionName() {
        return "list";
    }

    @Override
    public ToolResult execute(ArchiveFsContext ctx) {
        if (ctx.relativePath() == null) {
            return ToolResult.error("list 需要 relativePath 参数");
        }

        try {
            Path dir = ctx.guard().resolve(ctx.zone(), ctx.relativePath());
            if (!Files.isDirectory(dir)) {
                return ToolResult.error("路径不是目录: " + dir);
            }

            List<DirEntry> entries = Files.list(dir)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .limit(MAX_ENTRIES)
                    .map(p -> {
                        try {
                            return new DirEntry(
                                    p.getFileName().toString(),
                                    Files.isDirectory(p),
                                    Files.size(p),
                                    Files.getLastModifiedTime(p).toMillis()
                            );
                        } catch (IOException e) {
                            return new DirEntry(p.getFileName().toString(), Files.isDirectory(p), 0, 0);
                        }
                    })
                    .collect(Collectors.toList());

            boolean truncated = Files.list(dir).count() > MAX_ENTRIES;

            return ToolResult.ok(Map.of(
                    "action", "list",
                    "zone", ctx.zone(),
                    "path", ctx.relativePath(),
                    "entries", entries,
                    "total", entries.size(),
                    "truncated", truncated
            ));
        } catch (Exception e) {
            log.warn("[ListArchiveAction] 失败: {}", e.getMessage());
            return ToolResult.error("列出目录失败: " + e.getMessage());
        }
    }

    public record DirEntry(String name, boolean isDir, long sizeBytes, long lastModified) {}
}
