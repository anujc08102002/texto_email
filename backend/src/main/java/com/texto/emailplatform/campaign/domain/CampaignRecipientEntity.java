package com.texto.emailplatform.campaign.domain;

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
@Table(name = "campaign_recipients")
public class CampaignRecipientEntity {
	@Id private UUID id;
	@Column(name = "campaign_id", nullable = false) private UUID campaignId;
	@Column(name = "tenant_id", nullable = false) private UUID tenantId;
	@Column(name = "contact_id") private UUID contactId;
	@Column(nullable = false) private String email;
	@Column(nullable = false) private String status;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", nullable = false)
	private Map<String, Object> variables = new LinkedHashMap<>();
	@Column(name = "error_message") private String errorMessage;
	@Column(name = "email_message_id") private UUID emailMessageId;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;

	public static CampaignRecipientEntity queued(
			UUID campaignId,
			UUID tenantId,
			UUID contactId,
			String email,
			Map<String, Object> variables
	) {
		CampaignRecipientEntity recipient = new CampaignRecipientEntity();
		recipient.id = UUID.randomUUID();
		recipient.campaignId = campaignId;
		recipient.tenantId = tenantId;
		recipient.contactId = contactId;
		recipient.email = email;
		recipient.status = "PENDING";
		recipient.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
		recipient.createdAt = recipient.updatedAt = Instant.now();
		return recipient;
	}

	public void markAccepted(UUID messageId) {
		this.status = "ACCEPTED";
		this.emailMessageId = messageId;
		this.errorMessage = null;
		touch();
	}

	public void markSuppressed(UUID messageId) {
		this.status = "SUPPRESSED";
		this.emailMessageId = messageId;
		this.errorMessage = null;
		touch();
	}

	public void markFailed(String reason) {
		this.status = "FAILED";
		this.errorMessage = reason == null ? "Campaign recipient could not be queued" : reason.substring(0, Math.min(512, reason.length()));
		touch();
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public UUID getCampaignId() { return campaignId; }
	public UUID getTenantId() { return tenantId; }
	public UUID getContactId() { return contactId; }
	public String getEmail() { return email; }
	public String getStatus() { return status; }
	public Map<String, Object> getVariables() { return variables; }
	public String getErrorMessage() { return errorMessage; }
	public UUID getEmailMessageId() { return emailMessageId; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
