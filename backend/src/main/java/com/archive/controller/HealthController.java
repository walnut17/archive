package com.archive.controller;

import com.archive.common.ApiResponse;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 健康检查接口(简易版,生产可用 /actuator/health).
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthInfo> health() {
        return ApiResponse.ok(new HealthInfo("UP", Instant.now().toString()));
    }

    @Data
    public static class HealthInfo {
        private final String status;
        private final String time;
        public HealthInfo(String status, String time) {
            this.status = status;
            this.time = time;
        }
    }
}
