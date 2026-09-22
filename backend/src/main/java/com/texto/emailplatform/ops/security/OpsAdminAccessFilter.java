package com.texto.emailplatform.ops.security;

import com.texto.emailplatform.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * All {@code /api/v1/admin/**} routes except login require a platform-admin principal.
 * Tenant dashboard sessions and API keys are rejected.
 */
@Component
public class OpsAdminAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public OpsAdminAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        if (path.equals("/api/v1/admin/auth/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !path.startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
            return;
        }
        if (!(authentication.getPrincipal() instanceof PlatformAdminPrincipal)) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Platform admin authorization is required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message));
    }
}
