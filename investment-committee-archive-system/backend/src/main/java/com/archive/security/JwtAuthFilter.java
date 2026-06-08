package com.archive.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器.
 *
 * 每次请求检查 Authorization Header,如果有有效 JWT,设置 SecurityContext.
 * 没 token 或 token 无效 → 不设置,后续 Spring Security 会按未认证处理.
 *
 * @author Mavis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            log.debug("JWT 解析失败,跳过认证");
            chain.doFilter(request, response);
            return;
        }

        String username = claims.getSubject();
        String role = (String) claims.get("role");
        Long uid = ((Number) claims.get("uid")).longValue();

        // 把 uid 放在 details 里,Controller 可以取
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(uid, username, role),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role.toUpperCase())))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    /**
     * 注入到 SecurityContext 的 principal.
     */
    public record AuthenticatedUser(Long id, String username, String role) {
    }
}
