package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.common.StorageService;
import com.archive.dto.MaterialRequest;
import com.archive.dto.MaterialResponse;
import com.archive.dto.MaterialVersionResponse;
import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.service.MaterialService;
import com.archive.service.MaterialVersionService;
import com.archive.service.SectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 材料版本 API — 上传、下载、解析、章节切分.
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/materials/{materialId}/versions")
@RequiredArgsConstructor
public class MaterialVersionController {

    private final MaterialVersionService versionService;
    private final MaterialService materialService;
    private final StorageService storageService;
    private final SectionService sectionService;

    @GetMapping
    public ApiResponse<List<MaterialVersionResponse>> list(@PathVariable Long materialId) {
        List<MaterialVersion> versions = versionService.listByMaterialId(materialId);
        return ApiResponse.ok(versions.stream().map(MaterialVersionResponse::from).toList());
    }

    @GetMapping("/{versionId}")
    public ApiResponse<MaterialVersionResponse> getById(
            @PathVariable Long materialId,
            @PathVariable Long versionId) {
        MaterialVersion v = versionService.getById(versionId);
        if (!v.getMaterialId().equals(materialId)) {
            throw new IllegalArgumentException("版本不属于该材料");
        }
        return ApiResponse.ok(MaterialVersionResponse.from(v));
    }

    @PostMapping
    public ApiResponse<MaterialVersionResponse> upload(
            @PathVariable Long materialId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeNote", required = false) String changeNote,
            @AuthenticationPrincipal UserDetails user) {
        // 校验 material 存在
        materialService.getById(materialId);
        String uploadedBy = user != null ? user.getUsername() : "anonymous";
        MaterialVersion v = versionService.upload(materialId, file, changeNote, uploadedBy);
        return ApiResponse.ok(MaterialVersionResponse.from(v));
    }

    @PutMapping("/{versionId}/current")
    public ApiResponse<MaterialResponse> switchCurrent(
            @PathVariable Long materialId,
            @PathVariable Long versionId) {
        Material m = versionService.switchCurrentVersion(materialId, versionId);
        long versionCount = materialService.countVersions(materialId);
        return ApiResponse.ok(MaterialResponse.from(m, versionCount));
    }

    @DeleteMapping("/{versionId}")
    public ApiResponse<Void> delete(
            @PathVariable Long materialId,
            @PathVariable Long versionId) {
        MaterialVersion v = versionService.getById(versionId);
        if (!v.getMaterialId().equals(materialId)) {
            throw new IllegalArgumentException("版本不属于该材料");
        }
        versionService.deleteVersion(versionId);
        return ApiResponse.ok();
    }

    /**
     * 下载版本原始文件.
     */
    @GetMapping("/{versionId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long materialId,
            @PathVariable Long versionId) {
        MaterialVersion v = versionService.getById(versionId);
        if (!v.getMaterialId().equals(materialId)) {
            throw new IllegalArgumentException("版本不属于该材料");
        }
        try {
            byte[] bytes = storageService.readFileBytes(v.getStoragePath());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment",
                    java.net.URLEncoder.encode(v.getOriginalFilename(), "UTF-8"));
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 触发 Tika 重新解析(管理员操作).
     */
    @PostMapping("/{versionId}/reparse")
    public ApiResponse<Void> reparse(
            @PathVariable Long materialId,
            @PathVariable Long versionId) {
        versionService.parseVersion(versionId);
        return ApiResponse.ok();
    }

    /**
     * 章节切分(返回章节列表).
     * 用法:GET /api/materials/{mid}/versions/{vid}/sections
     */
    @GetMapping("/{versionId}/sections")
    public ApiResponse<List<Map<String, Object>>> sections(
            @PathVariable Long materialId,
            @PathVariable Long versionId) {
        MaterialVersion v = versionService.getById(versionId);
        if (!v.getMaterialId().equals(materialId)) {
            throw new IllegalArgumentException("版本不属于该材料");
        }
        if (v.getParsedTextPath() == null) {
            throw new IllegalStateException("版本尚未解析,请先调用 reparse");
        }
        try {
            String text = storageService.readParsedText(v.getParsedTextPath());
            List<SectionService.Section> sections = sectionService.split(text);
            List<Map<String, Object>> result = sections.stream().map(s -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("index", s.getIndex());
                map.put("title", s.getTitle());
                map.put("content", s.getContent());
                map.put("length", s.length());
                map.put("startOffset", s.getStartOffset());
                map.put("endOffset", s.getEndOffset());
                return map;
            }).toList();
            return ApiResponse.ok(result);
        } catch (Exception e) {
            throw new RuntimeException("章节切分失败: " + e.getMessage(), e);
        }
    }
}
