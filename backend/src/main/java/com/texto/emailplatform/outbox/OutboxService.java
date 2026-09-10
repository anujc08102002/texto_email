package com.texto.emailplatform.outbox;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public OutboxEventEntity enqueue(
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload
    ) {
        return outboxEventRepository.save(OutboxEventEntity.create(
                tenantId,
                aggregateType,
                aggregateId,
                eventType,
                payload
        ));
    }
}
