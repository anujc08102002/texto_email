package com.texto.emailplatform.outbox;

import com.texto.emailplatform.common.observability.RequestCorrelation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    public static final String EMAIL_DELIVERY_REQUESTED = "EMAIL_DELIVERY_REQUESTED";
    public static final String AGGREGATE_EMAIL_MESSAGE = "EmailMessage";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    public static OutboxEventEntity create(
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload
    ) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        RequestCorrelation.putInto(entity.payload);
        entity.requestId = RequestCorrelation.fromPayload(entity.payload, RequestCorrelation.PAYLOAD_REQUEST_ID);
        entity.traceId = RequestCorrelation.fromPayload(entity.payload, RequestCorrelation.PAYLOAD_TRACE_ID);
        if (entity.requestId == null) {
            entity.requestId = RequestCorrelation.requestId();
        }
        if (entity.traceId == null) {
            entity.traceId = RequestCorrelation.traceId();
        }
        entity.createdAt = Instant.now();
        entity.attempts = 0;
        return entity;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    public void recordPublishFailure(String error, Instant nextAttemptAt) {
        this.attempts++;
        this.lastError = error == null ? null : (error.length() <= 2000 ? error : error.substring(0, 2000));
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markDiscarded(String reason) {
        this.publishedAt = Instant.now();
        this.lastError = reason;
        this.nextAttemptAt = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }
}
