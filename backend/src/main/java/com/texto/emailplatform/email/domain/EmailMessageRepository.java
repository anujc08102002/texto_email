package com.texto.emailplatform.email.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailMessageRepository extends JpaRepository<EmailMessageEntity, UUID> {

    List<EmailMessageEntity> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<EmailMessageEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmailMessageEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

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
}
