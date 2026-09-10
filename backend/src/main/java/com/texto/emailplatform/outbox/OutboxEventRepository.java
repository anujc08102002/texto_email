package com.texto.emailplatform.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("""
            SELECT e FROM OutboxEventEntity e
            WHERE e.publishedAt IS NULL
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEventEntity> findUnpublished(Pageable pageable);
}
