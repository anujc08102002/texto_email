package com.texto.emailplatform.attachment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment_documents")
public class AttachmentDocumentEntity {
	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(name = "normalized_filename", nullable = false)
	private String normalizedFilename;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "object_key", nullable = false)
	private String objectKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "checksum_sha256", nullable = false, length = 64)
	private String checksumSha256;

	@Column(nullable = false)
	private String status;

	@Column(name = "scan_detail")
	private String scanDetail;

	@Column(name = "uploaded_by")
	private UUID uploadedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;
}
