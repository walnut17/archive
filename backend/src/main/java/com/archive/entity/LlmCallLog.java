package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * LLM 调用日志.
 *
 * <p>记录每次大模型调用的元数据(用户/场景/token/耗时/状态/错误),
 * 用于:用量统计 + 异常排查 + 合规审计.
 *
 * <p>埋点位置:GlmService.chat() / GlmService.rerank().
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "llm_call_log", indexes = {
        @Index(name = "idx_user_created", columnList = "userId, createdAt"),
        @Index(name = "idx_scenario_created", columnList = "scenario, createdAt"),
        @Index(name = "idx_created", columnList = "createdAt")
})
public class LlmCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    /** 场景:EXTRACTION / TIMEPOINT / COMPARE / QA / RERANK / SUMMARY. */
    @Column(nullable = false, length = 64)
    private String scenario;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
