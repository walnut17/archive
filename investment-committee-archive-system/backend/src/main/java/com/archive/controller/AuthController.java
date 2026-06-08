package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.security.JwtAuthFilter.AuthenticatedUser;
import com.archive.service.AuthService;
import com.archive.service.AuthService.LoginResult;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证相关接口.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录.
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@RequestBody LoginRequest req) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            return ApiResponse.fail("用户名和密码不能为空");
        }
        LoginResult result = authService.login(req.getUsername(), req.getPassword());
        if (!result.isSuccess()) {
            return ApiResponse.fail(40102, result.getMessage());
        }
        return ApiResponse.ok(result);
    }

    /**
     * 当前用户信息.
     */
    @GetMapping("/me")
    public ApiResponse<MeInfo> me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ApiResponse.fail(40101, "未登录");
        }
        return ApiResponse.ok(new MeInfo(user.id(), user.username(), user.role()));
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class MeInfo {
        private final Long id;
        private final String username;
        private final String role;

        public MeInfo(Long id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }
    }
}
