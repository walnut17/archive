package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.FactEventDiff;
import com.archive.dto.PageResponse;
import com.archive.dto.ProjectRequest;
import com.archive.dto.ProjectResponse;
import com.archive.dto.StagingUploadResponse;
import com.archive.entity.Project;
import com.archive.security.JwtAuthFilter;
import com.archive.engine.ExtractionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.archive.qaagent.QaAgentClient;
import com.archive.qaagent.QaAgentProperties;
import com.archive.service.GlmService;
import com.archive.service.ExportService;
import com.archive.service.AuditLogService;
import com.archive.service.NotificationService;
import com.archive.service.ProjectCreateStagingService;
import com.archive.service.ProjectFactEventService;
import com.archive.service.ProjectService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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
    private final ExportService exportService;
    private final ProjectFactEventService factEventService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ExtractionEngine extractionEngine;
    private final ProjectCreateStagingService stagingService;
    private final QaAgentProperties qaAgentProperties;

    @Autowired(required = false)
    private QaAgentClient qaAgentClient;

    @GetMapping
    public ApiResponse<PageResponse<ProjectResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageResponse<Project> result = projectService.list(page, size, status, keyword);
        return ApiResponse.ok(result.mapContent(ProjectResponse::from));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<ProjectResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        return ApiResponse.ok(projectService.getByIdMasked(id, userId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportList(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = "projects") String type) throws Exception {
        if (!"xlsx".equals(format)) {
            throw new IllegalArgumentException("列表导出仅支持 xlsx");
        }
        byte[] bytes = exportService.exportProjectsExcel(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/{id:\\d+}/export")
    public ResponseEntity<byte[]> exportProject(
            @PathVariable Long id,
            @RequestParam(defaultValue = "pdf") String format) throws Exception {
        byte[] bytes = "pdf".equals(format)
                ? exportService.exportProjectPdf(id)
                : exportService.exportSingleProjectExcel(id);
        String ext = "pdf".equals(format) ? "pdf" : "xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=project-" + id + "." + ext)
                .contentType("pdf".equals(format) ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/{projectId:\\d+}/fact-events/{eventId:\\d+}/diff")
    public ApiResponse<FactEventDiff> getFactDiff(
            @PathVariable Long projectId,
            @PathVariable Long eventId) {
        return ApiResponse.ok(factEventService.getDiff(eventId));
    }

    @PostMapping("/{id:\\d+}/unmask-request")
    public ApiResponse<Map<String, String>> requestUnmask(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        String actor = user != null ? user.username() : "anonymous";
        auditLogService.logSensitiveView(actor, "project", id, "unmask_request");
        notificationService.notifyAdmin("用户申请脱敏查看项目 " + id);
        return ApiResponse.ok(Map.of(
                "unmaskRequestUrl", "/api/projects/" + id + "?unmask=true&token=stub-" + userId));
    }

    /**
     * RI-16: 上传材料 → 草稿项目+议案+材料版本.
     */
    @PostMapping(value = "/staging-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StagingUploadResponse> stagingUpload(@RequestParam("file") MultipartFile file) {
        String uploadedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        return ApiResponse.ok(stagingService.stagingUpload(file, uploadedBy));
    }

    /**
     * 立项 AI 预填 — 优先 Python qa-agent，失败走 Java ExtractionEngine.
     */
    @PostMapping("/extract-preview")
    public ResponseEntity<ApiResponse<GlmService.ExtractionFailureResponse>> extractPreview(
            @RequestBody ExtractPreviewRequest body) {
        if (body.getMaterialVersionId() == null) {
            throw new IllegalArgumentException("materialVersionId 不能为空");
        }

        if (qaAgentProperties.isEnabled() && qaAgentClient != null) {
            QaAgentClient.ExtractionResult py = qaAgentClient.extractProjectFields(body.getMaterialVersionId());
            GlmService.ExtractionFailureResponse mapped = mapExtraction(py);
            if (!mapped.isSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(40030, mapped.getMessage(), mapped));
            }
            return ResponseEntity.ok(ApiResponse.ok(mapped));
        }

        GlmService.ExtractionFailureResponse result =
                extractionEngine.extractForPreview(body.getMaterialVersionId());
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(40030, result.getMessage(), result));
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private GlmService.ExtractionFailureResponse mapExtraction(QaAgentClient.ExtractionResult py) {
        ObjectMapper mapper = new ObjectMapper();
        GlmService.ExtractionFailureResponse r = new GlmService.ExtractionFailureResponse();
        r.setSuccess(py.isSuccess());
        if (py.getData() != null) {
            r.setData(mapper.valueToTree(py.getData()));
        }
        r.setMessage(py.getMessage());
        if (py.getFailureType() != null) {
            try {
                r.setFailureType(com.archive.common.FailureType.valueOf(py.getFailureType()));
            } catch (IllegalArgumentException ignored) {
                r.setFailureType(com.archive.common.FailureType.API_ERROR);
            }
        }
        r.setRetryable(Boolean.TRUE.equals(py.getRetryable()));
        return r;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody ProjectRequest req) {
        if (req.getMaterialVersionId() != null) {
            GlmService.ExtractionFailureResponse extract =
                    extractionEngine.extractForPreview(req.getMaterialVersionId());
            if (!extract.isSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(40030, extract.getMessage(), extract));
            }
        }
        Project created = projectService.create(req);
        return ResponseEntity.ok(ApiResponse.ok(ProjectResponse.from(created)));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<ProjectResponse> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        Project updated = projectService.update(id, req);
        return ApiResponse.ok(ProjectResponse.from(updated));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        projectService.softDelete(id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id:\\d+}/rollback")
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

    @Data
    public static class ExtractPreviewRequest {
        private Long materialVersionId;
    }
}
