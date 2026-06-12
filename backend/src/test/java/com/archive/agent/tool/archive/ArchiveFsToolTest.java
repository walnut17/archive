package com.archive.agent.tool.archive;

import com.archive.agent.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArchiveFsTool 集成测 — 使用 @TempDir 模拟文件系统.
 */
class ArchiveFsToolTest {

    private ArchivePathGuard guard;
    private ArchiveFsActionRegistry registry;
    private ArchiveFsTool tool;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // 创建模拟目录结构
        Path filesRoot = tempDir.resolve("files");
        Path parsedRoot = tempDir.resolve("parsed");
        filesRoot.toFile().mkdirs();
        parsedRoot.toFile().mkdirs();

        // 创建测试文件
        Path materialDir = filesRoot.resolve("project-1/material-12");
        materialDir.toFile().mkdirs();
        Files.writeString(materialDir.resolve("v1_report.txt"), "尽调报告内容：抵押物价值评估。");
        Files.writeString(materialDir.resolve("v1_attachment.csv"), "id,name,amount\n1,项目A,1000");

        // parsed 区
        Path parsedDir = parsedRoot.resolve("12");
        parsedDir.toFile().mkdirs();
        Files.writeString(parsedDir.resolve("v1_parsed.txt"), "Tika 解析后的文本内容，包含风险点分析。");

        guard = new ArchivePathGuard(
                filesRoot.toAbsolutePath().toString(),
                parsedRoot.toAbsolutePath().toString()
        );

        registry = new ArchiveFsActionRegistry(List.of(
                new ListArchiveAction(),
                new GrepArchiveAction(),
                new ReadTextArchiveAction()
        ));

        tool = new ArchiveFsTool(registry, guard);
    }

    @Test
    void testListFiles() {
        AgentContext ctx = new AgentContext("test");
        Map<String, Object> args = Map.of(
                "action", "list",
                "zone", "files",
                "relativePath", "project-1/material-12"
        );
        var result = tool.execute(args, ctx);
        assertTrue(result.isOk());
    }

    @Test
    void testGrepText() {
        AgentContext ctx = new AgentContext("test");
        Map<String, Object> args = Map.of(
                "action", "grep",
                "zone", "files",
                "relativePath", "project-1/material-12/v1_report.txt",
                "pattern", "抵押物"
        );
        var result = tool.execute(args, ctx);
        assertTrue(result.isOk());
    }

    @Test
    void testReadText() {
        AgentContext ctx = new AgentContext("test");
        Map<String, Object> args = Map.of(
                "action", "read",
                "zone", "files",
                "relativePath", "project-1/material-12/v1_report.txt"
        );
        var result = tool.execute(args, ctx);
        assertTrue(result.isOk());
    }

    @Test
    void testUnknownAction() {
        AgentContext ctx = new AgentContext("test");
        Map<String, Object> args = Map.of("action", "delete", "zone", "files");
        var result = tool.execute(args, ctx);
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("未知 action"));
    }

    @Test
    void testPathTraversalRejected() {
        AgentContext ctx = new AgentContext("test");
        Map<String, Object> args = Map.of(
                "action", "read",
                "zone", "files",
                "relativePath", "../config/config.json"
        );
        var result = tool.execute(args, ctx);
        assertFalse(result.isOk());
    }

    @Test
    void testGrepNonTextFile_returnsError() {
        AgentContext ctx = new AgentContext("test");
        Map<String, Object> args = Map.of(
                "action", "grep",
                "zone", "files",
                "relativePath", "project-1/material-12/v1_attachment.csv",
                "pattern", "amount"
        );
        var result = tool.execute(args, ctx);
        assertTrue(result.isOk()); // .csv is allowed
    }
}
