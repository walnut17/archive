package com.archive.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tika 文本提取服务.
 *
 * 支持格式:docx / doc / pdf / txt / xlsx / xls / pptx / html / xml / json / md
 * 不支持:扫描件 PDF / 图片(扫描件 OCR 用 GLM-4V,M5 阶段做)
 *
 * @author Mavis
 */
@Slf4j
@Service
public class TikaService {

    private final Tika tika = new Tika();

    /**
     * 从字节数组提取文本.
     */
    public String extractText(byte[] bytes) {
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes)) {
            return tika.parseToString(bis);
        } catch (IOException | TikaException e) {
            throw new RuntimeException("Tika parse failed", e);
        }
    }

    /**
     * 从文件路径提取文本.
     */
    public String extractText(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            return tika.parseToString(is);
        } catch (IOException | TikaException e) {
            throw new RuntimeException("Tika parse failed: " + file, e);
        }
    }

    /**
     * 检测 MIME 类型(从文件名后缀 + 内容嗅探).
     */
    public String detectMimeType(byte[] bytes, String filename) {
        try {
            if (filename != null && !filename.isBlank()) {
                return tika.detect(bytes, filename);
            }
            return tika.detect(bytes);
        } catch (IOException e) {
            log.warn("MimeType detect failed for filename={}", filename, e);
            return "application/octet-stream";
        }
    }
}
