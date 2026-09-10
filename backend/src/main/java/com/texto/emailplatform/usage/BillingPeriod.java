package com.texto.emailplatform.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Calendar month billing window in UTC. {@code end} is the first day of the next month (exclusive).
 */
public record BillingPeriod(LocalDate start, LocalDate end) {

    public static BillingPeriod current() {
        return of(Instant.now());
    }

    public static BillingPeriod of(Instant instant) {
        LocalDate start = LocalDate.ofInstant(instant, ZoneOffset.UTC).withDayOfMonth(1);
        return new BillingPeriod(start, start.plusMonths(1));
    }

    public Instant startInstant() {
        return start.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant endInstant() {
        return end.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
