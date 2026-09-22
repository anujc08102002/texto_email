package com.texto.emailplatform.outbox;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEventEntity, UUID>, JpaSpecificationExecutor<OutboxEventEntity> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockPublishable(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();
}
