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

    @Query("""
            SELECT s FROM SuppressionEntity s
            WHERE s.tenantId = :tenantId
              AND (:type IS NULL OR s.type = :type)
              AND (:search IS NULL OR s.normalizedEmail LIKE CONCAT('%', :search, '%'))
            ORDER BY s.createdAt DESC
            """)
    List<SuppressionEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("type") String type,
            @Param("search") String search
    );
}
