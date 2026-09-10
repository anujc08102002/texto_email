package com.texto.emailplatform.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "templates")
public class TemplateEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, length = 128)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TemplateEntity create(UUID tenantId, String name, String slug, String description, UUID createdBy) {
        Instant now = Instant.now();
        TemplateEntity entity = new TemplateEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.name = name;
        entity.slug = slug;
        entity.description = description;
        entity.status = STATUS_DRAFT;
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void patch(String name, String description) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        this.updatedAt = Instant.now();
    }

    public void setCurrentVersionId(UUID currentVersionId) {
        this.currentVersionId = currentVersionId;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status = STATUS_ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = STATUS_ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
