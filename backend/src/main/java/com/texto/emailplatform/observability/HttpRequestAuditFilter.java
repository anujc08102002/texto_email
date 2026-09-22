package com.texto.emailplatform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HttpRequestAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestAuditFilter.class);

    private final HttpRequestAuditRecorder recorder;

    public HttpRequestAuditFilter(HttpRequestAuditRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CountingHttpServletResponse counting = new CountingHttpServletResponse(response);
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, counting);
        } finally {
            try {
                recorder.record(request, counting, started, counting.byteCount());
            } catch (Exception exception) {
                log.warn("HTTP request audit persist failed type={}", exception.getClass().getSimpleName());
            }
        }
    }
}
