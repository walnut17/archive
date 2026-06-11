package com.archive.service;

import com.archive.entity.Role;
import com.archive.entity.User;
import com.archive.repository.RoleRepository;
import com.archive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * RBAC 双轨角色服务 — user_role 优先, user.role_id 兜底.
 *
 * @author Mavis
 */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * 判断用户是否拥有指定角色(不区分大小写).
     */
    public boolean hasRole(Long userId, String roleName) {
        if (userId == null || roleName == null || roleName.isBlank()) {
            return false;
        }
        String normalized = roleName.trim().toLowerCase();

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM user_role ur
                JOIN role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND LOWER(r.code) = ?
                """,
                Integer.class,
                userId,
                normalized
        );
        if (count != null && count > 0) {
            return true;
        }

        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getRoleId() != null) {
            Optional<Role> role = roleRepository.findById(user.get().getRoleId());
            return role.isPresent() && normalized.equalsIgnoreCase(role.get().getCode());
        }
        return false;
    }

    /**
     * 获取用户全部角色 code(小写, user_role 优先合并 role_id).
     */
    public List<String> getRoleCodes(Long userId) {
        Set<String> roles = new LinkedHashSet<>();
        if (userId == null) {
            return List.of();
        }

        jdbcTemplate.query(
                """
                SELECT LOWER(r.code) FROM user_role ur
                JOIN role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY ur.assigned_at
                """,
                rs -> roles.add(rs.getString(1)),
                userId
        );

        if (roles.isEmpty()) {
            userRepository.findById(userId).ifPresent(user -> {
                if (user.getRoleId() != null) {
                    roleRepository.findById(user.getRoleId())
                            .ifPresent(role -> roles.add(role.getCode().toLowerCase()));
                }
            });
        }

        if (roles.isEmpty()) {
            roles.add("user");
        }
        return new ArrayList<>(roles);
    }

    /**
     * 为用户分配角色(幂等).
     */
    public void assignRole(Long userId, String roleCode) {
        Role role = roleRepository.findByCode(roleCode.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleCode));
        jdbcTemplate.update(
                """
                INSERT IGNORE INTO user_role (user_id, role_id, assigned_at)
                VALUES (?, ?, NOW())
                """,
                userId,
                role.getId()
        );
    }

    /**
     * 移除用户角色.
     */
    public void removeRole(Long userId, String roleCode) {
        Role role = roleRepository.findByCode(roleCode.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleCode));
        jdbcTemplate.update(
                "DELETE FROM user_role WHERE user_id = ? AND role_id = ?",
                userId,
                role.getId()
        );
    }
}
