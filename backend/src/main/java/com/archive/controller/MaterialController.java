package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.MaterialRequest;
import com.archive.dto.MaterialResponse;
import com.archive.dto.PageResponse;
import com.archive.entity.Material;
import com.archive.security.JwtAuthFilter;
import com.archive.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 材料 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public ApiResponse<PageResponse<MaterialResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long proposalId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageResponse<Material> result = materialService.list(page, size, proposalId, category, status, keyword);
        return ApiResponse.ok(PageResponse.<MaterialResponse>builder()
                .content(result.getContent().stream()
                        .map(m -> MaterialResponse.from(m, materialService.countVersions(m.getId())))
                        .toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build());
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialResponse> getById(@PathVariable Long id) {
        Material m = materialService.getById(id);
        return ApiResponse.ok(MaterialResponse.from(m, materialService.countVersions(id)));
    }

    @PostMapping
    public ApiResponse<MaterialResponse> create(@Valid @RequestBody MaterialRequest req) {
        Material created = materialService.create(req);
        return ApiResponse.ok(MaterialResponse.from(created, 0L));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialResponse> update(@PathVariable Long id, @Valid @RequestBody MaterialRequest req) {
        Material updated = materialService.update(id, req);
        return ApiResponse.ok(MaterialResponse.from(updated, materialService.countVersions(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','LEGAL')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        materialService.softDelete(id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/batch")
    public ApiResponse<List<MaterialResponse>> batchUpload(
            @RequestParam Long proposalId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false) String defaultCategory,
            @RequestParam(required = false) String defaultTags) {
        String uploadedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Material> created = materialService.batchUpload(proposalId, files, defaultCategory, defaultTags, uploadedBy);
        List<MaterialResponse> responses = created.stream()
                .map(m -> MaterialResponse.from(m, materialService.countVersions(m.getId())))
                .toList();
        return ApiResponse.ok(responses);
    }
}
