package com.archive.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置.
 *
 * M0 阶段策略:
 * - 启用 CORS(开发期前端 http://localhost:5173 调用)
 * - CSRF 关闭(前后端分离,纯 JWT 鉴权)
 * - Session 策略:无状态(STATELESS)
 * - JWT 过滤器放在 UsernamePasswordAuthenticationFilter 之前
 * - 公开路径:/api/auth/login、/api/health、/actuator/**
 * - 字典管理(除查询选项外) → admin 权限
 * - 字典查询选项 → 已认证即可
 * - 抽取方法/比对方法/审计日志 → admin 权限
 * - 其他 /api/** → 需要认证
 *
 * @author Mavis
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 字典查询选项(公开字典数据):任何已认证用户可用(必须在 /api/dict/** 之前)
                        .requestMatchers(HttpMethod.GET, "/api/dict/options/**").authenticated()
                        // 字典管理:增删改查全部需要 admin 权限
                        .requestMatchers(HttpMethod.GET, "/api/dict/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/dict/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/dict/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/dict/**").hasAuthority("ROLE_ADMIN")
                        // 抽取方法/比对方法/审计日志:仅 admin
                        .requestMatchers("/api/extraction-methods/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/comparison-methods/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/audit-logs/**").hasAuthority("ROLE_ADMIN")
                        // 其他 /api/** 需要认证(任意角色)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // 开发期:本地前端端口全放行;生产期改具体域名
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*:*",
                "http://10.*:*",
                "http://172.16.*:*",
                "http://182.*:*",
                "https://*"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
