package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.service.AuditLogService;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 客户端错误上报端点.
 * 前端全局 errorHandler 捕获的异常通过 POST 上报到此端点.
 */
@RestController
@RequestMapping("/api/client-error")
@RequiredArgsConstructor
public class ClientErrorController {

    private static final Logger log = LoggerFactory.getLogger(ClientErrorController.class);
    private final AuditLogService auditLogService;

    @PostMapping
    public ApiResponse<Void> report(@RequestBody ClientErrorRequest req) {
        String username = req.getUserId() != null ? "user:" + req.getUserId() : "anonymous";
        auditLogService.logClientError(username, req.getMessage(), req.getStack(), req.getUrl());
        log.warn("[ClientError] user={}, url={}, msg={}", username, req.getUrl(), req.getMessage());
        return ApiResponse.ok();
    }

    @Data
    public static class ClientErrorRequest {
        @Size(max = 2000)
        private String message;
        @Size(max = 10000)
        private String stack;
        @Size(max = 500)
        private String url;
        private String userId;
        private String timestamp;
    }
}
