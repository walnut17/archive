package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.LlmUsageStats;
import com.archive.service.LlmUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 用量统计接口.
 *
 * <ul>
 *   <li>{@code GET /api/llm/my-usage} — 当前用户的用量(任何角色)</li>
 *   <li>{@code GET /api/llm/stats} — 全员聚合(仅 admin)</li>
 *   <li>{@code GET /api/llm/recent} — 最近 N 条(任何角色看自己,admin 看全员)</li>
 * </ul>
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmUsageController {

    private final LlmUsageService service;

    /**
     * 当前用户自己的用量.
     */
    @GetMapping("/my-usage")
    public ApiResponse<LlmUsageStats> myUsage(Authentication auth,
                                              @RequestParam(defaultValue = "50") int recentLimit) {
        String username = (auth != null) ? auth.getName() : null;
        return ApiResponse.ok(service.getUsage(username, recentLimit));
    }

    /**
     * 全员聚合(仅 admin).
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LlmUsageStats> stats(@RequestParam(defaultValue = "50") int recentLimit) {
        return ApiResponse.ok(service.getUsage(null, recentLimit));
    }
}
