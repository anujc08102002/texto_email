package com.texto.emailplatform.ops.security;

import com.texto.emailplatform.auth.security.DashboardSessionAuthenticationFilter;
import com.texto.emailplatform.ops.OpsAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PlatformAdminAuthenticationFilter extends OncePerRequestFilter {

    private final OpsAuthService opsAuthService;

    public PlatformAdminAuthenticationFilter(OpsAuthService opsAuthService) {
        this.opsAuthService = opsAuthService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null
                || header.isBlank()
                || !header.regionMatches(true, 0, DashboardSessionAuthenticationFilter.BEARER_PREFIX, 0,
                DashboardSessionAuthenticationFilter.BEARER_PREFIX.length())) {
            filterChain.doFilter(request, response);
            return;
        }
        String rawToken = header.substring(DashboardSessionAuthenticationFilter.BEARER_PREFIX.length()).trim();
        var principal = opsAuthService.authenticateToken(rawToken);
        if (principal.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal.get(),
                "redacted",
                List.of(new SimpleGrantedAuthority(OpsAuthorization.ROLE_PLATFORM_ADMIN))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
