package com.archive.service;

import com.archive.entity.User;
import com.archive.repository.RoleRepository;
import com.archive.repository.UserRepository;
import com.archive.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 登录.返回 LoginResult(null 表示失败,错误信息在 message).
     */
    @Transactional
    public LoginResult login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return LoginResult.fail("用户名或密码错误");
        }
        User user = userOpt.get();

        if (!"在岗".equals(user.getStatus())) {
            return LoginResult.fail("账号已停用");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return LoginResult.fail("用户名或密码错误");
        }

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
