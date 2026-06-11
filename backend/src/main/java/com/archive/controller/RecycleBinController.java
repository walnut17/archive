package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 回收站 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    @GetMapping("/{entityType}")
    @PreAuthorize("hasAnyRole('ADMIN','PM','SECRETARY')")
    public ApiResponse<List<Map<String, Object>>> list(
            @PathVariable String entityType,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(recycleBinService.listDeleted(entityType, limit));
    }

    @PostMapping("/{entityType}/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ApiResponse<Void> restore(@PathVariable String entityType, @PathVariable Long id) {
        recycleBinService.restore(entityType, id);
        return ApiResponse.ok();
    }
}
