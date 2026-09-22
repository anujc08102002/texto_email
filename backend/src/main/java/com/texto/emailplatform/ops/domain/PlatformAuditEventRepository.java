package com.texto.emailplatform.ops.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformAuditEventRepository
        extends JpaRepository<PlatformAuditEventEntity, UUID>, JpaSpecificationExecutor<PlatformAuditEventEntity> {

    Page<PlatformAuditEventEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
            Instant from,
            Instant to,
            Pageable pageable
    );
}
