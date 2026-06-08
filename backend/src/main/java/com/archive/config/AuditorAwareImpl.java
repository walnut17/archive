package com.archive.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA 审计者感知实现.
 *
 * 为 @CreatedBy / @LastModifiedBy 提供当前用户名。
 * 未认证请求返回 "system"，确保 created_by 不为 null。
 *
 * @author Mavis
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.of("system");
        }
        return Optional.of(auth.getName());
    }
}
