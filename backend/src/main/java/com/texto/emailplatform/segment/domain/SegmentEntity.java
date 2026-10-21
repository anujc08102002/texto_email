package com.texto.emailplatform.segment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "segments")
public class SegmentEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String name;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false) private String definition;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public static SegmentEntity create(UUID tenantId, String name, String definition) { SegmentEntity segment=new SegmentEntity(); segment.id=UUID.randomUUID(); segment.tenantId=tenantId; segment.name=name; segment.definition=definition; segment.createdAt=segment.updatedAt=Instant.now(); return segment; }
    public void update(String name,String definition){this.name=name;this.definition=definition;this.updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getName(){return name;} public String getDefinition(){return definition;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
