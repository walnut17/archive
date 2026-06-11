package com.archive.agent.tool.archive;

import com.archive.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 读取文本文件内容（read）.
 * 按 maxBytes 截断，避免 LLM 上下文溢出.
 */
@Component
public class ReadTextArchiveAction implements ArchiveFsAction {

    private static final Logger log = LoggerFactory.getLogger(ReadTextArchiveAction.class);

    @Override
    public String actionName() {
        return "read";
    }

    @Override
    public ToolResult execute(ArchiveFsContext ctx) {
        if (ctx.relativePath() == null) {
            return ToolResult.error("read 需要 relativePath 参数");
        }

        try {
            Path file = ctx.guard().resolve(ctx.zone(), ctx.relativePath());
            if (!Files.isRegularFile(file) || !isTextFile(file)) {
                return ToolResult.error("路径不是文本文件或无法读取: " + file);
            }

            long fileSize = Files.size(file);
            byte[] bytes = Files.readAllBytes(file);
            boolean truncated = bytes.length > ctx.maxBytes();

            String content;
            if (truncated) {
                content = new String(bytes, 0, ctx.maxBytes(), StandardCharsets.UTF_8)
                        + "\n\n... (截断, 文件大小 " + fileSize + " 字节)";
            } else {
                content = new String(bytes, StandardCharsets.UTF_8);
            }

            return ToolResult.ok(Map.of(
                    "action", "read",
                    "zone", ctx.zone(),
                    "path", ctx.relativePath(),
                    "content", content,
                    "fileSizeBytes", fileSize,
                    "truncated", truncated
            ));
        } catch (IOException e) {
            log.warn("[ReadTextArchiveAction] 读取失败: {}", e.getMessage());
            return ToolResult.error("读取文件失败: " + e.getMessage());
        }
    }

    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")
                || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".html");
    }
}
