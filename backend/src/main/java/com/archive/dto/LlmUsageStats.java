package com.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LLM 用量统计响应.
 *
 * @author Mavis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsageStats {
    /** 今日统计. */
    private PeriodStats today;
    /** 本周. */
    private PeriodStats thisWeek;
    /** 本月. */
    private PeriodStats thisMonth;

    /** 按场景聚合. */
    private List<BucketRow> byScenario;
    /** 按用户聚合. */
    private List<BucketRow> byUser;

    /** 最近 N 条原始记录. */
    private List<RecentCall> recent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodStats {
        private long count;
        private Long totalTokens;  // 可空
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BucketRow {
        /** 场景名 / 用户名. */
        private String key;
        private long count;
        private Long totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentCall {
        private Long id;
        private String username;
        private String scenario;
        private Integer durationMs;
        private String status;
        private String createdAt;
    }
}
