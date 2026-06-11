package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.PageResponse;
import com.archive.dto.ProjectRequest;
import com.archive.dto.ProjectResponse;
import com.archive.entity.Project;
import com.archive.security.JwtAuthFilter;
import com.archive.service.ProjectService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 项目 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<PageResponse<ProjectResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageResponse<Project> result = projectService.list(page, size, status, keyword);
        return ApiResponse.ok(result.mapContent(ProjectResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(ProjectResponse.from(projectService.getById(id)));
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectRequest req) {
        Project created = projectService.create(req);
        return ApiResponse.ok(ProjectResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectResponse> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        Project updated = projectService.update(id, req);
        return ApiResponse.ok(ProjectResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        projectService.softDelete(id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ApiResponse<ProjectResponse> rollback(
            @PathVariable Long id,
            @RequestBody RollbackRequest req,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        Project rolled = projectService.rollback(id, req.getTargetVersion(), userId);
        return ApiResponse.ok(ProjectResponse.from(rolled));
    }

    @Data
    public static class RollbackRequest {
        private int targetVersion;
    }
}
