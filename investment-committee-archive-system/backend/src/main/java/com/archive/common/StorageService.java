package com.archive.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 文件存储服务 — 负责把上传的原始文件落到本地 file-root,
 * 解析后的纯文本落到 parsed-root.
 *
 * 配置项(在 config.json 里):
 *   storage.file-root:   D:/archive/files
 *   storage.parsed-root: D:/archive/parsed
 *
 * @author Mavis
 */
@Slf4j
@Service
public class StorageService {

    @Value("${app.storage.file-root}")
    private String fileRoot;

    @Value("${app.storage.parsed-root}")
    private String parsedRoot;

    /**
     * 把上传的文件保存到 file-root 下的相对路径,返回绝对路径.
     *
     * @param relativePath 如 "project-001/proposal-001/material-001/v1/report.pdf"
     * @param in           输入流(Spring 会自动关闭)
     * @return 保存后的绝对路径
     */
    public Path saveFile(String relativePath, InputStream in) throws IOException {
        Path target = resolveUnderRoot(fileRoot, relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved file: {}", target);
        return target;
    }

    /**
     * 把解析后的纯文本保存到 parsed-root 下的相对路径.
     */
    public Path saveParsedText(String relativePath, String text) throws IOException {
        Path target = resolveUnderRoot(parsedRoot, relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text);
        log.info("Saved parsed text: {} ({} chars)", target, text.length());
        return target;
    }

    /**
     * 读 parsed-root 下的文件内容.
     */
    public String readParsedText(String relativePath) throws IOException {
        Path target = resolveUnderRoot(parsedRoot, relativePath);
        return Files.readString(target);
    }

    /**
     * 读 file-root 下的文件字节.
     */
    public byte[] readFileBytes(String relativePath) throws IOException {
        Path target = resolveUnderRoot(fileRoot, relativePath);
        return Files.readAllBytes(target);
    }

    /**
     * 检查文件是否存在.
     */
    public boolean fileExists(String relativePath) {
        Path target = resolveUnderRoot(fileRoot, relativePath);
        return Files.exists(target);
    }

    /**
     * 删除 file-root 下的文件(慎用,版本管理会用到).
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path target = resolveUnderRoot(fileRoot, relativePath);
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", relativePath, e);
            return false;
        }
    }

    /**
     * 拼接 root + relative,自动处理路径分隔符.
     * 防止 relative 里含 ".." 跳出 root 范围.
     */
    private Path resolveUnderRoot(String root, String relative) {
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        Path relPath = Paths.get(relative).normalize();
        Path target = rootPath.resolve(relPath).normalize();
        if (!target.startsWith(rootPath)) {
            throw new IllegalArgumentException("Path traversal attempt: " + relative);
        }
        return target;
    }
}
