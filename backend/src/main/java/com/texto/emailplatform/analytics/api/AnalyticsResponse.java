package com.texto.emailplatform.analytics.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(
        Instant from,
        Instant to,
        Totals totals,
        Rates rates,
        List<Point> series
) {
    public record Totals(long accepted, long sent, long delivered, long deferred, long failed,
                         long bounced, long complained, long suppressed) { }
    public record Rates(double deliveryRate, double bounceRate, double complaintRate, double suppressionRate) { }
    public record Point(LocalDate date, long accepted, long sent, long delivered, long deferred, long failed,
                        long bounced, long complained, long suppressed) { }
}
