package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.entity.User;
import com.archive.repository.UserRepository;
import com.archive.service.RbacService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员用户与角色 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final RbacService rbacService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserSummary>> list() {
        List<UserSummary> users = userRepository.findAll().stream()
                .map(u -> {
                    UserSummary s = new UserSummary();
                    s.setId(u.getId());
                    s.setUsername(u.getUsername());
                    s.setDisplayName(u.getDisplayName());
                    s.setStatus(u.getStatus());
                    s.setRoles(rbacService.getRoleCodes(u.getId()));
                    return s;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(users);
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<String>> getRoles(@PathVariable Long id) {
        return ApiResponse.ok(rbacService.getRoleCodes(id));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> assignRole(@PathVariable Long id, @RequestBody RoleAssignRequest req) {
        rbacService.assignRole(id, req.getRoleCode());
        return ApiResponse.ok(Map.of(
                "userId", id,
                "roles", rbacService.getRoleCodes(id)
        ));
    }

    @DeleteMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> removeRole(@PathVariable Long id, @PathVariable String roleCode) {
        rbacService.removeRole(id, roleCode);
        return ApiResponse.ok(Map.of(
                "userId", id,
                "roles", rbacService.getRoleCodes(id)
        ));
    }

    @Data
    public static class UserSummary {
        private Long id;
        private String username;
        private String displayName;
        private String status;
        private List<String> roles;
    }

    @Data
    public static class RoleAssignRequest {
        private String roleCode;
    }
}
