package com.archive.repository;

import com.archive.entity.LlmCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM 调用日志仓储.
 *
 * @author Mavis
 */
@Repository
public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    /** 按用户 + 时间段分页查. */
    Page<LlmCallLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /** 按用户名 + 时间段分页查. */
    Page<LlmCallLog> findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(
            String username, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /** 按时间窗口查所有. */
    Page<LlmCallLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /** 时间段内总调用次数. */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 时间段内总 token 消耗(可为 NULL 字段用 COALESCE). */
    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM LlmCallLog l " +
            "WHERE l.createdAt BETWEEN :start AND :end")
    Long sumTotalTokensBetween(@Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /** 按场景聚合(token 消耗,NULL 视为 0). */
    @Query("SELECT l.scenario, COUNT(l), COALESCE(SUM(l.totalTokens), 0) " +
            "FROM LlmCallLog l " +
            "WHERE l.createdAt BETWEEN :start AND :end " +
            "GROUP BY l.scenario " +
            "ORDER BY COUNT(l) DESC")
    List<Object[]> groupByScenarioBetween(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** 按用户聚合. */
    @Query("SELECT l.username, COUNT(l), COALESCE(SUM(l.totalTokens), 0) " +
            "FROM LlmCallLog l " +
            "WHERE l.createdAt BETWEEN :start AND :end " +
            "GROUP BY l.username " +
            "ORDER BY COUNT(l) DESC")
    List<Object[]> groupByUserBetween(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /** 用户名 + 时间段. */
    long countByUsernameAndCreatedAtBetween(String username, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM LlmCallLog l " +
            "WHERE l.username = :username AND l.createdAt BETWEEN :start AND :end")
    Long sumTotalTokensByUsernameBetween(@Param("username") String username,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);
}
