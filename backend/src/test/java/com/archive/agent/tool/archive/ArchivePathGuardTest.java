package com.archive.agent.tool.archive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArchivePathGuard 单测 — 路径越界防护.
 */
class ArchivePathGuardTest {

    private ArchivePathGuard guard;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        // 用临时目录模拟 D:/archive/files 和 D:/archive/parsed
        Path filesRoot = tempDir.resolve("files");
        Path parsedRoot = tempDir.resolve("parsed");
        filesRoot.toFile().mkdirs();
        parsedRoot.toFile().mkdirs();

        guard = new ArchivePathGuard(
                filesRoot.toAbsolutePath().toString(),
                parsedRoot.toAbsolutePath().toString()
        );
    }

    @Test
    void resolveNormalPath() {
        Path resolved = guard.resolve("files", "project-1/v1_report.pdf");
        assertTrue(resolved.toString().endsWith("project-1/v1_report.pdf"));
    }

    @Test
    void resolvePathTraversal_throws() {
        assertThrows(SecurityException.class, () ->
                guard.resolve("files", "../config/config.json"));
    }

    @Test
    void resolveDoubleDotTraversal_throws() {
        assertThrows(SecurityException.class, () ->
                guard.resolve("parsed", "valid/../../etc/passwd"));
    }

    @Test
    void resolveAbsolutePath_throws() {
        assertThrows(SecurityException.class, () ->
                guard.resolve("files", "D:/windows/system32/config"));
    }

    @Test
    void resolveInvalidZone_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                guard.resolve("logs", "something.log"));
    }

    @Test
    void resolveParsedZoneOk() {
        Path resolved = guard.resolve("parsed", "material-12/v3_parsed.txt");
        assertTrue(resolved.toString().endsWith("material-12/v3_parsed.txt"));
    }
}
