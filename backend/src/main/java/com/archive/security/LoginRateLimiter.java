package com.archive.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流器.
 *
 * <p>基于 IP 的登录限流:
 * <ul>
 *   <li>每分钟最多 5 次尝试</li>
 *   <li>连续 5 次失败锁定 15 分钟</li>
 * </ul>
 *
 * @author Mavis
 */
@Component
public class LoginRateLimiter {

    /** 每分钟最大尝试次数. */
    private static final int MAX_ATTEMPTS_PER_MINUTE = 5;

    /** 连续失败锁定阈值. */
    private static final int LOCK_THRESHOLD = 5;

    /** 锁定时间(分钟). */
    private static final int LOCK_DURATION_MINUTES = 15;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 检查指定 IP 是否被限流.
     *
     * @param ip 客户端 IP
     * @return true 如果被限流
     */
    public boolean isBlocked(String ip) {
        Attempt att = attempts.get(ip);
        if (att == null) {
            return false;
        }

        // 检查是否在锁定期
        if (att.lockUntil != null) {
            if (Instant.now().isBefore(att.lockUntil)) {
                return true; // 仍被锁定
            } else {
                // 锁定已过期,清除
                attempts.remove(ip);
                return false;
            }
        }

        // 检查 1 分钟内是否超过最大尝试次数
        if (att.attempts >= MAX_ATTEMPTS_PER_MINUTE) {
            Instant windowStart = att.firstAttempt;
            if (windowStart != null && Instant.now().minusSeconds(60).isBefore(windowStart)) {
                // 在 1 分钟窗口内超过上限,视为需要锁定
                att.lockUntil = Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60);
                return true;
            }
            // 窗口已过期,重置
            attempts.remove(ip);
            return false;
        }

        return false;
    }

    /**
     * 记录登录失败.
     */
    public void recordFailure(String ip) {
        Attempt att = attempts.computeIfAbsent(ip, k -> new Attempt());
        att.attempts++;

        if (att.firstAttempt == null) {
            att.firstAttempt = Instant.now();
        }

        // 检查是否需要立即锁定(连续 5 次失败)
        if (att.attempts >= LOCK_THRESHOLD) {
            att.lockUntil = Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60);
        }
    }

    /**
     * 清除指定 IP 的限流记录(登录成功时调用).
     */
    public void reset(String ip) {
        attempts.remove(ip);
    }

    /**
     * 尝试记录.
     */
    static class Attempt {
        /** 尝试次数. */
        int attempts;
        /** 第一次尝试时间(用于 1 分钟窗口). */
        Instant firstAttempt;
        /** 锁定到期时间(null 表示未锁定). */
        Instant lockUntil;
    }
}
