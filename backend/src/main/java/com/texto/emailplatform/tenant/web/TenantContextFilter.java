package com.texto.emailplatform.tenant.web;

import com.texto.emailplatform.auth.security.ApiKeyPrincipal;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
                    TenantContext.set(apiKeyPrincipal.tenantId());
                } else if (principal instanceof DashboardPrincipal dashboardPrincipal) {
                    TenantContext.set(dashboardPrincipal.tenantId());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
