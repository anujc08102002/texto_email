package com.texto.emailplatform.email.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailMessageRepository extends JpaRepository<EmailMessageEntity, UUID>, JpaSpecificationExecutor<EmailMessageEntity> {

    List<EmailMessageEntity> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<EmailMessageEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmailMessageEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<EmailMessageEntity> findByTenantIdAndRfc822MessageId(UUID tenantId, String rfc822MessageId);

    Optional<EmailMessageEntity> findByTenantIdAndProviderMessageId(UUID tenantId, String providerMessageId);

    Optional<EmailMessageEntity> findByBounceCorrelationToken(String bounceCorrelationToken);

    List<EmailMessageEntity> findByRfc822MessageId(String rfc822MessageId);

    List<EmailMessageEntity> findByProviderMessageId(String providerMessageId);

    Page<EmailMessageEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<EmailMessageEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query("""
            SELECT m FROM EmailMessageEntity m
            WHERE m.tenantId = :tenantId
              AND (:status IS NULL OR m.status = :status)
              AND (
                   LOWER(m.subject) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.recipient) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<EmailMessageEntity> searchByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("q") String q,
            Pageable pageable
    );

    @Query("""
            SELECT m.status, COUNT(m)
            FROM EmailMessageEntity m
            WHERE (:tenantId IS NULL OR m.tenantId = :tenantId)
            GROUP BY m.status
            """)
    java.util.List<Object[]> countGroupedByStatus(@Param("tenantId") UUID tenantId);

    java.util.List<EmailMessageEntity> findTop20ByOrderByCreatedAtDesc();

    java.util.List<EmailMessageEntity> findTop20ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
