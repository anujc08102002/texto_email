package com.texto.emailplatform.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tenant_usage")
public class TenantUsageEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "metric", nullable = false, length = 128)
    private String metric;

    @Column(name = "used", nullable = false)
    private long used;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TenantUsageEntity create(UUID tenantId, LocalDate periodStart, LocalDate periodEnd, String metric) {
        TenantUsageEntity entity = new TenantUsageEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.periodStart = periodStart;
        entity.periodEnd = periodEnd;
        entity.metric = metric;
        entity.used = 0L;
        entity.updatedAt = Instant.now();
        return entity;
    }

    /**
     * Overwrites the counter for metrics that track a live resource count rather than an accumulating total.
     */
    public void recordUsed(long value) {
        this.used = value;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getMetric() {
        return metric;
    }

    public long getUsed() {
        return used;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
