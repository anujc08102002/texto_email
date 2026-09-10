package com.texto.emailplatform.template.domain;

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
@Table(name = "template_versions")
public class TemplateVersionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "subject", nullable = false, length = 998)
    private String subject;

    @Column(name = "html_content", nullable = false)
    private String htmlContent;

    @Column(name = "text_content")
    private String textContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> variablesSchema = new LinkedHashMap<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TemplateVersionEntity create(
            UUID templateId,
            int version,
            String subject,
            String htmlContent,
            String textContent,
            Map<String, Object> variablesSchema,
            UUID createdBy
    ) {
        TemplateVersionEntity entity = new TemplateVersionEntity();
        entity.id = UUID.randomUUID();
        entity.templateId = templateId;
        entity.version = version;
        entity.subject = subject;
        entity.htmlContent = htmlContent;
        entity.textContent = textContent;
        entity.variablesSchema = variablesSchema == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variablesSchema);
        entity.createdBy = createdBy;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public int getVersion() {
        return version;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public String getTextContent() {
        return textContent;
    }

    public Map<String, Object> getVariablesSchema() {
        return variablesSchema;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
