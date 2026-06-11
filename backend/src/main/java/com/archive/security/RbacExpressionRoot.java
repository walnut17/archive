package com.archive.security;

import com.archive.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@code @PreAuthorize} SpEL 根对象 — 5 角色权限矩阵.
 *
 * <p>用法: {@code @PreAuthorize("@rbac.hasAnyRole(authentication, 'ADMIN', 'PM')")}</p>
 *
 * @author Mavis
 */
@Component("rbac")
@RequiredArgsConstructor
public class RbacExpressionRoot {

    private final RbacService rbacService;

    public boolean hasRole(Authentication auth, String role) {
        Long userId = resolveUserId(auth);
        if (userId == null) {
            return false;
        }
        return rbacService.hasRole(userId, role);
    }

    public boolean hasAnyRole(Authentication auth, String... roles) {
        Long userId = resolveUserId(auth);
        if (userId == null) {
            return false;
        }
        for (String role : roles) {
            if (rbacService.hasRole(userId, role)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin(Authentication auth) {
        return hasRole(auth, "admin");
    }

    public boolean isPm(Authentication auth) {
        return hasRole(auth, "pm");
    }

    public boolean isLegal(Authentication auth) {
        return hasRole(auth, "legal");
    }

    public boolean isCommittee(Authentication auth) {
        return hasRole(auth, "committee");
    }

    public boolean isSecretary(Authentication auth) {
        return hasRole(auth, "secretary");
    }

    private Long resolveUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof JwtAuthFilter.AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }
}
