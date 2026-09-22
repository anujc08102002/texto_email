package com.texto.emailplatform.observability.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HttpRequestAuditRepository
        extends JpaRepository<HttpRequestAuditEntity, UUID>, JpaSpecificationExecutor<HttpRequestAuditEntity> {

    @Modifying
    @Query("delete from HttpRequestAuditEntity e where e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
