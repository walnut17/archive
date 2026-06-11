package com.archive.service;

import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.common.StorageService;
import com.archive.repository.MaterialRepository;
import com.archive.repository.MaterialVersionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 附件预览服务 (RI-65).
 */
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final long MAX_PREVIEW_BYTES = 50L * 1024 * 1024;

    private final MaterialRepository materialRepository;
    private final MaterialVersionRepository materialVersionRepository;
    private final StorageService storageService;

    public PreviewContent getForPreview(Long materialId, Integer version) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + materialId));

        MaterialVersion mv;
        if (version != null) {
            mv = materialVersionRepository.findByMaterialIdAndVersionNo(materialId, version)
                    .orElseThrow(() -> new NoSuchElementException("版本不存在: v" + version));
        } else if (material.getCurrentVersionId() != null) {
            mv = materialVersionRepository.findById(material.getCurrentVersionId())
                    .orElseThrow(() -> new NoSuchElementException("当前版本不存在"));
        } else {
            mv = materialVersionRepository.findFirstByMaterialIdOrderByVersionNoDesc(materialId)
                    .orElseThrow(() -> new NoSuchElementException("材料无版本"));
        }

        if (mv.getFileSize() != null && mv.getFileSize() > MAX_PREVIEW_BYTES) {
            throw new IllegalStateException("文件超过 50MB,请下载查看");
        }

        byte[] content;
        try {
            content = storageService.readFileBytes(mv.getStoragePath());
        } catch (Exception e) {
            throw new IllegalStateException("读取文件失败: " + e.getMessage(), e);
        }

        String mimeType = mv.getMimeType() != null ? mv.getMimeType() : "application/octet-stream";
        return new PreviewContent(content, mimeType, mv.getOriginalFilename());
    }

    @Data
    public static class PreviewContent {
        private final byte[] content;
        private final String mimeType;
        private final String filename;
    }
}
