package com.texto.emailplatform.ops;

import com.texto.emailplatform.common.api.RequestIdHolder;
import com.texto.emailplatform.ops.security.PlatformAdminPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OpsAuditFilter extends OncePerRequestFilter {

    private final OpsAuditRecorder auditRecorder;

    public OpsAuditFilter(OpsAuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/admin/") || path.equals("/api/v1/admin/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof PlatformAdminPrincipal principal) {
                auditRecorder.record(
                        principal.adminId(),
                        request.getMethod(),
                        request.getRequestURI(),
                        redactQuery(request.getQueryString()),
                        response.getStatus(),
                        (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - started),
                        RequestIdHolder.current(),
                        request.getRemoteAddr()
                );
            }
        }
    }

    static String redactQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("token") || lower.contains("password") || lower.contains("secret") || lower.contains("key")) {
            return "[redacted]";
        }
        return query.length() <= 1024 ? query : query.substring(0, 1024);
    }
}
