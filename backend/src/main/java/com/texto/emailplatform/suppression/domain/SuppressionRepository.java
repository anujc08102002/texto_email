package com.texto.emailplatform.suppression.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuppressionRepository extends JpaRepository<SuppressionEntity, UUID> {

    Optional<SuppressionEntity> findByTenantIdAndNormalizedEmail(UUID tenantId, String normalizedEmail);

    Optional<SuppressionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SuppressionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<SuppressionEntity> findByTenantIdAndTypeOrderByCreatedAtDesc(UUID tenantId, String type);

    // Only invoked with a non-null search term. Binding a null into CONCAT/LIKE makes
    // PostgreSQL type the parameter as bytea ("operator does not exist: varchar ~~ bytea"),
    // so the null-search cases use the derived finders above instead.
    @Query("""
            SELECT s FROM SuppressionEntity s
            WHERE s.tenantId = :tenantId
              AND (:type IS NULL OR s.type = :type)
              AND s.normalizedEmail LIKE CONCAT('%', :search, '%')
            ORDER BY s.createdAt DESC
            """)
    List<SuppressionEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("type") String type,
            @Param("search") String search
    );
}
