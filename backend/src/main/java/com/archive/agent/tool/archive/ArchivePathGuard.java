package com.archive.agent.tool.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Archive 路径安全守卫.
 * 校验所有路径在合法白名单根目录下，防止 path traversal 攻击.
 */
@Component
public class ArchivePathGuard {

    private static final Logger log = LoggerFactory.getLogger(ArchivePathGuard.class);

    private final Path fileRoot;
    private final Path parsedRoot;
    private final List<Path> allowedRoots;

    public ArchivePathGuard(
            @Value("${app.storage.file-root:D:/archive/files}") String fileRootStr,
            @Value("${app.storage.parsed-root:D:/archive/parsed}") String parsedRootStr) {
        this.fileRoot = Paths.get(fileRootStr).normalize().toAbsolutePath();
        this.parsedRoot = Paths.get(parsedRootStr).normalize().toAbsolutePath();
        this.allowedRoots = List.of(this.fileRoot, this.parsedRoot);
    }

    /**
     * 解析并校验路径是否在合法 root 下.
     * @return 规范化的绝对路径
     * @throws SecurityException 如果路径越界
     */
    public Path resolve(String zone, String relativePath) {
        Path root = switch (zone) {
            case "files" -> fileRoot;
            case "parsed" -> parsedRoot;
            default -> throw new IllegalArgumentException("未知 zone: " + zone + "，仅支持 files/parsed");
        };

        Path resolved = root.resolve(relativePath).normalize().toAbsolutePath();

        if (!resolved.startsWith(root)) {
            log.warn("[ArchivePathGuard] 路径越界: zone={}, relativePath={}, resolved={}", zone, relativePath, resolved);
            throw new SecurityException("路径越界: " + resolved + " 不在 " + root + " 下");
        }

        return resolved;
    }

    public Path getFileRoot() { return fileRoot; }
    public Path getParsedRoot() { return parsedRoot; }
    public List<Path> getAllowedRoots() { return allowedRoots; }
}
