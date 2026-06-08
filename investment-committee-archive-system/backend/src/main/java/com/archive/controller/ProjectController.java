package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.PageResponse;
import com.archive.dto.ProjectRequest;
import com.archive.dto.ProjectResponse;
import com.archive.entity.Project;
import com.archive.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok();
    }
}
