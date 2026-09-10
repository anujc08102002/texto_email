package com.texto.emailplatform.usage;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.usage.api.UsageResponse;
import com.texto.emailplatform.usage.api.UsageResponse.MetricUsage;
import com.texto.emailplatform.usage.domain.TenantUsageEntity;
import com.texto.emailplatform.usage.domain.TenantUsageRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {

    private final TenantUsageRepository tenantUsageRepository;
    private final EntitlementService entitlementService;

    public UsageService(TenantUsageRepository tenantUsageRepository, EntitlementService entitlementService) {
        this.tenantUsageRepository = tenantUsageRepository;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public UsageResponse getUsage(UUID tenantId) {
        BillingPeriod period = BillingPeriod.current();
        Map<String, Long> used = new LinkedHashMap<>();
        tenantUsageRepository.findByTenantIdAndPeriodStartOrderByMetricAsc(tenantId, period.start())
                .forEach(entry -> used.put(entry.getMetric(), entry.getUsed()));

        List<MetricUsage> metrics = new ArrayList<>();
        for (String metric : UsageMetrics.ALL) {
            long consumed = used.getOrDefault(metric, 0L);
            OptionalLong limit = entitlementService.getLimit(tenantId, metric);
            Long limitValue = limit.isPresent() ? limit.getAsLong() : null;
            Long remaining = limitValue == null ? null : Math.max(0L, limitValue - consumed);
            metrics.add(new MetricUsage(metric, consumed, limitValue, remaining));
        }
        return new UsageResponse(tenantId, period.start(), period.end(), metrics);
    }

    /**
     * Creates the usage rows for the current period so later increments only ever update.
     */
    @Transactional
    public void ensurePeriodRows(UUID tenantId) {
        UsageMetrics.ALL.forEach(metric -> ensurePeriodRow(tenantId, metric));
    }

    @Transactional
    public void ensurePeriodRow(UUID tenantId, String metric) {
        BillingPeriod period = BillingPeriod.current();
        tenantUsageRepository.insertPeriodRowIfAbsent(
                UUID.randomUUID(),
                tenantId,
                period.start(),
                period.end(),
                metric
        );
    }

    /**
     * Overwrites the counter for a metric that tracks a live resource count (API keys, domains, users).
     */
    @Transactional
    public void recordUsage(UUID tenantId, String metric, long value) {
        BillingPeriod period = BillingPeriod.current();
        tenantUsageRepository.insertPeriodRowIfAbsent(UUID.randomUUID(), tenantId, period.start(), period.end(), metric);
        tenantUsageRepository.findByTenantIdAndPeriodStartAndMetric(tenantId, period.start(), metric)
                .ifPresent(entry -> {
                    entry.recordUsed(value);
                    tenantUsageRepository.save(entry);
                });
    }

    /**
     * Increments usage atomically, rejecting the call when it would exceed the plan limit.
     *
     * @return {@code false} when the quota is exhausted
     */
    @Transactional
    public boolean tryConsumeQuota(UUID tenantId, String metric, long amount) {
        return increment(tenantId, metric, amount);
    }

    /**
     * Same as {@link #tryConsumeQuota} but fails the request when the quota is exhausted.
     */
    @Transactional
    public void consumeQuota(UUID tenantId, String metric, long amount) {
        if (!increment(tenantId, metric, amount)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "QUOTA_EXCEEDED",
                    "Plan quota for " + metric + " has been exhausted for this billing period"
            );
        }
    }

    private boolean increment(UUID tenantId, String metric, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        BillingPeriod period = BillingPeriod.current();
        tenantUsageRepository.insertPeriodRowIfAbsent(UUID.randomUUID(), tenantId, period.start(), period.end(), metric);
        OptionalLong limit = entitlementService.getLimit(tenantId, metric);
        Long limitValue = limit.isPresent() ? limit.getAsLong() : null;
        return tenantUsageRepository.incrementUsageWithinLimit(
                tenantId,
                period.start(),
                metric,
                amount,
                limitValue
        ) > 0;
    }

    @Transactional(readOnly = true)
    public long currentUsage(UUID tenantId, String metric) {
        return tenantUsageRepository
                .findByTenantIdAndPeriodStartAndMetric(tenantId, BillingPeriod.current().start(), metric)
                .map(TenantUsageEntity::getUsed)
                .orElse(0L);
    }
}
