package com.texto.emailplatform.usage.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantUsageRepository extends JpaRepository<TenantUsageEntity, UUID> {

    List<TenantUsageEntity> findByTenantIdAndPeriodStartOrderByMetricAsc(UUID tenantId, LocalDate periodStart);

    Optional<TenantUsageEntity> findByTenantIdAndPeriodStartAndMetric(UUID tenantId, LocalDate periodStart, String metric);

    /**
     * Creates the period row only when it does not exist yet, so concurrent callers cannot collide.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tenant_usage (id, tenant_id, period_start, period_end, metric, used, updated_at)
            VALUES (:id, :tenantId, :periodStart, :periodEnd, :metric, 0, NOW())
            ON CONFLICT (tenant_id, period_start, metric) DO NOTHING
            """, nativeQuery = true)
    int insertPeriodRowIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("metric") String metric
    );

    /**
     * Atomically increments usage while enforcing the plan limit in the same statement.
     * A {@code null} limit means unlimited. Returns the number of rows updated (0 when the quota is exhausted).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE tenant_usage
            SET used = used + :amount, updated_at = NOW()
            WHERE tenant_id = :tenantId
              AND period_start = :periodStart
              AND metric = :metric
              AND (CAST(:limitValue AS BIGINT) IS NULL OR used + :amount <= CAST(:limitValue AS BIGINT))
            """, nativeQuery = true)
    int incrementUsageWithinLimit(
            @Param("tenantId") UUID tenantId,
            @Param("periodStart") LocalDate periodStart,
            @Param("metric") String metric,
            @Param("amount") long amount,
            @Param("limitValue") Long limitValue
    );
}
