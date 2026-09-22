package com.texto.emailplatform.observability;

import com.texto.emailplatform.auth.security.ApiKeyPrincipal;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.observability.RequestCorrelation;
import com.texto.emailplatform.observability.domain.HttpRequestAuditEntity;
import com.texto.emailplatform.ops.security.PlatformAdminPrincipal;
import com.texto.emailplatform.tenant.web.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HttpRequestAuditRecorder {

    private final HttpRequestAuditRepositorySupport repositorySupport;

    public HttpRequestAuditRecorder(HttpRequestAuditRepositorySupport repositorySupport) {
        this.repositorySupport = repositorySupport;
    }

    public void record(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAtMillis,
            long responseBytes
    ) {
        Instant occurredAt = Instant.ofEpochMilli(startedAtMillis);
        int durationMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0, System.currentTimeMillis() - startedAtMillis));
        int status = response.getStatus();
        String route = HttpRouteNormalizer.normalize(request.getRequestURI());
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        UUID tenantId = TenantContext.get().orElse(null);
        UUID principalId = null;
        UUID apiKeyId = null;
        String authOutcome = "ANONYMOUS";
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof DashboardPrincipal dashboard) {
                principalId = dashboard.userId();
                tenantId = tenantId == null ? dashboard.tenantId() : tenantId;
                authOutcome = "AUTHENTICATED";
            } else if (principal instanceof ApiKeyPrincipal apiKey) {
                principalId = apiKey.apiKeyId();
                apiKeyId = apiKey.apiKeyId();
                tenantId = tenantId == null ? apiKey.tenantId() : tenantId;
                authOutcome = "AUTHENTICATED";
            } else if (principal instanceof PlatformAdminPrincipal admin) {
                principalId = admin.adminId();
                authOutcome = "AUTHENTICATED";
            } else if (!"anonymousUser".equals(principal)) {
                authOutcome = "AUTHENTICATED";
            }
        }
        if (status == 401) {
            authOutcome = "UNAUTHENTICATED";
        } else if (status == 403 && !"AUTHENTICATED".equals(authOutcome)) {
            authOutcome = "FORBIDDEN";
        } else if (status == 403) {
            authOutcome = "FORBIDDEN";
        }

        String errorCode = HttpRequestAuditSanitizer.defaultErrorCode(
                status,
                HttpRequestAuditAttributes.errorCode(request)
        );
        String errorCategory = HttpRequestAuditAttributes.errorCategory(request);
        if (errorCategory == null) {
            errorCategory = HttpRequestAuditSanitizer.defaultErrorCategory(status);
        }
        if (status < 400) {
            errorCode = null;
            errorCategory = null;
        }

        long requestSize = request.getContentLengthLong();
        String operationType = HttpRequestAuditAttributes.operationType(request);
        if (operationType == null) {
            operationType = HttpRouteNormalizer.operationType(method, route);
        }
        HttpRequestAuditEntity entity = HttpRequestAuditEntity.create(
                RequestCorrelation.requestIdOrUnknown(),
                RequestCorrelation.traceId(),
                occurredAt,
                tenantId,
                principalId,
                apiKeyId,
                method,
                route,
                status,
                durationMs,
                requestSize >= 0 ? requestSize : null,
                responseBytes,
                HttpRequestAuditSanitizer.sanitizeClientIp(
                        request.getHeader("X-Forwarded-For"),
                        request.getRemoteAddr()
                ),
                HttpRequestAuditSanitizer.clip(request.getHeader("User-Agent"), 512),
                HttpRequestAuditSanitizer.clip(request.getHeader("Referer"), 512),
                authOutcome,
                errorCode,
                errorCategory,
                firstNonNull(
                        HttpRequestAuditAttributes.emailMessageId(request),
                        HttpRouteNormalizer.extractEmailMessageId(request.getRequestURI())
                ),
                HttpRequestAuditSanitizer.clip(HttpRequestAuditAttributes.providerMessageId(request), 255),
                HttpRequestAuditSanitizer.clip(operationType, 64)
        );
        repositorySupport.saveIsolated(entity);
    }

    private static UUID firstNonNull(UUID primary, UUID fallback) {
        return primary == null ? fallback : primary;
    }

    @Component
    public static class HttpRequestAuditRepositorySupport {

        private final com.texto.emailplatform.observability.domain.HttpRequestAuditRepository repository;

        public HttpRequestAuditRepositorySupport(
                com.texto.emailplatform.observability.domain.HttpRequestAuditRepository repository
        ) {
            this.repository = repository;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void saveIsolated(HttpRequestAuditEntity entity) {
            repository.save(entity);
        }
    }
}
