package com.archive.service;

import com.archive.dto.LlmUsageStats;
import com.archive.entity.LlmCallLog;
import com.archive.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 用量统计服务.
 *
 * <p>提供"个人用量"和"全员聚合"两种查询.
 * 全员聚合由 admin 角色调用,普通用户只能查自己.
 *
 * @author Mavis
 */
@Service
@RequiredArgsConstructor
public class LlmUsageService {

    private final LlmCallLogRepository repo;

    /**
     * 查某个用户(或全员)的用量.
     *
     * @param username  null = 全员聚合(仅 admin)
     * @param recentLimit 最近 N 条
     */
    @Transactional(readOnly = true)
    public LlmUsageStats getUsage(String username, int recentLimit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today0 = LocalDate.now();
        LocalDate weekStart = today0.minusDays(today0.getDayOfWeek().getValue() - 1L);
        LocalDate monthStart = today0.withDayOfMonth(1);

        LlmUsageStats.PeriodStats todayStats = username == null
                ? aggregate(null, today0.atStartOfDay(), now.plusSeconds(1))
                : aggregateByUsername(username, today0.atStartOfDay(), now.plusSeconds(1));
        LlmUsageStats.PeriodStats weekStats = username == null
                ? aggregate(null, weekStart.atStartOfDay(), now.plusSeconds(1))
                : aggregateByUsername(username, weekStart.atStartOfDay(), now.plusSeconds(1));
        LlmUsageStats.PeriodStats monthStats = username == null
                ? aggregate(null, monthStart.atStartOfDay(), now.plusSeconds(1))
                : aggregateByUsername(username, monthStart.atStartOfDay(), now.plusSeconds(1));

        // 按场景 / 按用户聚合(只对全员模式有意义)
        List<LlmUsageStats.BucketRow> byScenario;
        List<LlmUsageStats.BucketRow> byUser;
        if (username == null) {
            byScenario = repo.groupByScenarioBetween(
                            monthStart.atStartOfDay(), now.plusSeconds(1))
                    .stream()
                    .map(row -> LlmUsageStats.BucketRow.builder()
                            .key((String) row[0])
                            .count((Long) row[1])
                            .totalTokens((Long) row[2])
                            .build())
                    .collect(Collectors.toList());
            byUser = repo.groupByUserBetween(
                            monthStart.atStartOfDay(), now.plusSeconds(1))
                    .stream()
                    .map(row -> LlmUsageStats.BucketRow.builder()
                            .key((String) row[0] == null ? "(system)" : (String) row[0])
                            .count((Long) row[1])
                            .totalTokens((Long) row[2])
                            .build())
                    .collect(Collectors.toList());
        } else {
            byScenario = List.of();
            byUser = List.of();
        }

        // 最近 N 条
        List<LlmCallLog> recentLogs = (username == null)
                ? repo.findByCreatedAtBetweenOrderByCreatedAtDesc(
                        now.minusDays(30), now.plusSeconds(1),
                        PageRequest.of(0, recentLimit)).getContent()
                : repo.findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(
                        username,
                        now.minusDays(30), now.plusSeconds(1),
                        PageRequest.of(0, recentLimit)).getContent();

        List<LlmUsageStats.RecentCall> recent = recentLogs.stream()
                .map(l -> LlmUsageStats.RecentCall.builder()
                        .id(l.getId())
                        .username(l.getUsername())
                        .scenario(l.getScenario())
                        .durationMs(l.getDurationMs())
                        .status(l.getStatus())
                        .createdAt(l.getCreatedAt() == null ? null
                                : l.getCreatedAt().toString())
                        .build())
                .collect(Collectors.toList());

        return LlmUsageStats.builder()
                .today(todayStats)
                .thisWeek(weekStats)
                .thisMonth(monthStats)
                .byScenario(byScenario)
                .byUser(byUser)
                .recent(recent)
                .build();
    }

    private LlmUsageStats.PeriodStats aggregate(String ignore, LocalDateTime start, LocalDateTime end) {
        long count = repo.countByCreatedAtBetween(start, end);
        Long tokens = repo.sumTotalTokensBetween(start, end);
        return LlmUsageStats.PeriodStats.builder()
                .count(count)
                .totalTokens(tokens)
                .build();
    }

    private LlmUsageStats.PeriodStats aggregateByUsername(String username, LocalDateTime start, LocalDateTime end) {
        long count = repo.countByUsernameAndCreatedAtBetween(username, start, end);
        Long tokens = repo.sumTotalTokensByUsernameBetween(username, start, end);
        return LlmUsageStats.PeriodStats.builder()
                .count(count)
                .totalTokens(tokens)
                .build();
    }
}
