package com.archive.service;

import com.archive.entity.User;
import com.archive.repository.RoleRepository;
import com.archive.repository.UserRepository;
import com.archive.security.JwtUtil;
import com.archive.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 认证服务.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * 登录.返回 LoginResult(null 表示失败,错误信息在 message).
     */
    @Transactional
    public LoginResult login(String username, String password) {
        // 获取客户端 IP(通过 RequestContextHolder)
        String ip = getClientIp();

        // 检查登录频率限制
        if (loginRateLimiter.isBlocked(ip)) {
            throw new IllegalStateException("登录过于频繁,请15分钟后再试");
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            loginRateLimiter.recordFailure(ip);
            return LoginResult.fail("用户名或密码错误");
        }
        User user = userOpt.get();

        if (!"在岗".equals(user.getStatus())) {
            loginRateLimiter.recordFailure(ip);
            return LoginResult.fail("账号已停用");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginRateLimiter.recordFailure(ip);
            return LoginResult.fail("用户名或密码错误");
        }

        // 登录成功,重置限流
        loginRateLimiter.reset(ip);

        // 查询角色
        String roleCode = "USER";
        if (user.getRoleId() != null) {
            roleCode = roleRepository.findById(user.getRoleId())
                    .map(r -> r.getCode())
                    .orElse("USER");
        }

        // 更新最后登录时间
        userRepository.updateLastLoginAt(user.getId(), LocalDateTime.now());

        // 生成 JWT
        String token = jwtUtil.generate(user.getId(), user.getUsername(), roleCode);

        return LoginResult.ok(token, user.getId(), user.getUsername(), user.getDisplayName(), roleCode);
    }

    /**
     * 从当前请求中获取客户端 IP.
     */
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理的情况,取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 登录结果.
     */
    @Data
    public static class LoginResult {
        private boolean success;
        private String message;
        private String token;
        private Long userId;
        private String username;
        private String displayName;
        private String role;

        public static LoginResult ok(String token, Long userId, String username, String displayName, String role) {
            LoginResult r = new LoginResult();
            r.success = true;
            r.token = token;
            r.userId = userId;
            r.username = username;
            r.displayName = displayName;
            r.role = role;
            return r;
        }

        public static LoginResult fail(String message) {
            LoginResult r = new LoginResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
