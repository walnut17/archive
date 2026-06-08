package com.archive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 投委会档案管理系统 - 后端启动类.
 *
 * @author Mavis
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class ArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchiveApplication.class, args);
    }

    /**
     * JPA 审计者:从 SecurityContext 取当前用户名,未认证返回 "system"。
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return java.util.Optional.of("system");
            }
            return java.util.Optional.of(auth.getName());
        };
    }
}
