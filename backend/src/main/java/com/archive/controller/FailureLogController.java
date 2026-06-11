package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.service.FailureLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 失败兜底日志 API (admin).
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/failure-logs")
@RequiredArgsConstructor
public class FailureLogController {

    private final FailureLogService failureLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "false") boolean resolved,
            @RequestParam(defaultValue = "50") int limit) {
        if (resolved) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(failureLogService.listUnresolved(limit));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resolve(@PathVariable Long id) {
        failureLogService.markResolved(id);
        return ApiResponse.ok();
    }
}
