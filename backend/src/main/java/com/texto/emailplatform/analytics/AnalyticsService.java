package com.texto.emailplatform.analytics;

import com.texto.emailplatform.analytics.api.AnalyticsResponse;
import com.texto.emailplatform.auth.security.ApiKeyPrincipal;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.RolePermissions;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public AnalyticsResponse get(Instant requestedFrom, Instant requestedTo) {
        authorizeRead();
        UUID tenantId = TenantContext.get().orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required"));
        Instant to = requestedTo == null ? Instant.now() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(Duration.ofDays(30)) : requestedFrom;
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ApiException(400, "INVALID_ANALYTICS_RANGE", "Analytics range must be between 1 second and 90 days");
        }

        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) buckets.put(day, new long[8]);

        jdbc.query("""
                SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                    count(*) AS accepted,
                    count(*) FILTER (WHERE status IN ('SENDING','SENT','DELIVERED','DEFERRED','FAILED','BOUNCED')) AS sent,
                    count(*) FILTER (WHERE status = 'DELIVERED') AS delivered,
                    count(*) FILTER (WHERE status = 'DEFERRED') AS deferred,
                    count(*) FILTER (WHERE status = 'FAILED') AS failed,
                    count(*) FILTER (WHERE status = 'BOUNCED') AS bounced,
                    count(*) FILTER (WHERE status = 'SUPPRESSED') AS suppressed
                FROM email_messages
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY day ORDER BY day
                """, rs -> {
            long[] values = buckets.computeIfAbsent(
                    rs.getObject("day", LocalDate.class),
                    ignored -> new long[8]
            );

            values[0] = rs.getLong("accepted");
            values[1] = rs.getLong("sent");
            values[2] = rs.getLong("delivered");
            values[3] = rs.getLong("deferred");
            values[4] = rs.getLong("failed");
            values[5] = rs.getLong("bounced");
            values[7] = rs.getLong("suppressed");
        }, tenantId, from, to);


        jdbc.query("""
                SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                    count(*) AS complained
                FROM complaint_events
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY day ORDER BY day
                """, rs -> {
            LocalDate day = rs.getObject("day", LocalDate.class);
            long[] values = buckets.computeIfAbsent(day, ignored -> new long[8]);
            values[6] = rs.getLong("complained");
        }, tenantId, from, to);


        // IMPORTANT: outside both query callbacks
        long[] total = new long[8];

        List<AnalyticsResponse.Point> series = buckets.entrySet().stream().map(entry -> {
            long[] v = entry.getValue();

            for (int i = 0; i < v.length; i++) {
                total[i] += v[i];
            }

            return new AnalyticsResponse.Point(
                    entry.getKey(),
                    v[0],
                    v[1],
                    v[2],
                    v[3],
                    v[4],
                    v[5],
                    v[6],
                    v[7]
            );
        }).toList();

        var totals = new AnalyticsResponse.Totals(
                total[0],
                total[1],
                total[2],
                total[3],
                total[4],
                total[5],
                total[6],
                total[7]
        );

        var rates = new AnalyticsResponse.Rates(
                rate(total[2], total[0]),
                rate(total[5], total[0]),
                rate(total[6], total[0]),
                rate(total[7], total[0])
        );

        return new AnalyticsResponse(from, to, totals, rates, series);
    }

    private static double rate(long value, long base) { return base == 0 ? 0 : Math.round(value * 10000.0 / base) / 10000.0; }

    private static void authorizeRead() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof DashboardPrincipal dashboard && !RolePermissions.has(dashboard.role(), Permission.EMAIL_READ)) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access is denied");
        }
        if (principal instanceof ApiKeyPrincipal apiKey && !apiKey.hasScope("email:read")) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "API_KEY_SCOPE_REQUIRED", "API key requires email:read scope");
        }
    }
}
