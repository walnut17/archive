package com.archive.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 全局异常处理.
 *
 * @author Mavis
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** D-3: v1.1 灰度默认 false; application.yml 配置属 MOD-05 */
    @Value("${archive.optimistic-lock.strict:false}")
    private boolean optimisticLockStrict;

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleOptimisticLock(
            OptimisticLockException e, HttpServletRequest req) {
        if (!optimisticLockStrict) {
            log.warn("Optimistic lock conflict (v1.1 灰度, 仅记日志) @ {}: {}", req.getRequestURI(), e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "warning", "数据已被他人修改，请刷新后重试",
                    "version", -1
            )));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(40901,
                "数据已被他人修改，请刷新后重试"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException e, HttpServletRequest req) {
        log.info("资源不存在 @ {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(40400, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleState(IllegalStateException e, HttpServletRequest req) {
        log.info("状态不允许 @ {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(40001, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest req) {
        log.warn("参数错误 @ {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(40000, e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException e, HttpServletRequest req) {
        log.warn("认证失败 @ {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(40101, "认证失败"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        log.warn("权限不足 @ {}: {}", req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(40301, "权限不足"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception e, HttpServletRequest req) {
        log.error("未处理异常 @ {}", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(50000, "服务器内部错误"));
    }
}
