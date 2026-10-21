package com.texto.emailplatform.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class CampaignEntity {
	@Id private UUID id;
	@Column(name = "tenant_id", nullable = false) private UUID tenantId;
	@Column(nullable = false) private String name;
	@Column(nullable = false) private String status;
	private String sender;
	@Column(name = "reply_to") private String replyTo;
	private String subject;
	private String preheader;
	@Column(name = "html_body") private String htmlBody;
	@Column(name = "text_body") private String textBody;
	@Column(name = "template_id") private UUID templateId;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name="attachment_ids",nullable=false) private java.util.List<UUID> attachmentIds=new java.util.ArrayList<>();
    @Column(name = "template_version") private Integer templateVersion;
	@Column(name = "segment_id") private UUID segmentId;
	@Column(name = "contact_list_id") private UUID contactListId;
	@Column(name = "scheduled_at") private Instant scheduledAt;
	private String timezone;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "dispatch_lease_id") private UUID dispatchLeaseId;
    @Column(name = "dispatch_lease_until") private Instant dispatchLeaseUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;

	public static CampaignEntity draft(
			UUID tenantId,
			String name,
			String sender,
			String replyTo,
			String subject,
			String preheader,
			String html,
			String text,
			UUID templateId,
			UUID createdBy
	) {
		CampaignEntity campaign = new CampaignEntity();
		campaign.id = UUID.randomUUID();
		campaign.tenantId = tenantId;
		campaign.name = name;
		campaign.status = "DRAFT";
		campaign.sender = sender;
		campaign.replyTo = replyTo;
		campaign.subject = subject;
		campaign.preheader = preheader;
		campaign.htmlBody = html;
		campaign.textBody = text;
		campaign.templateId = templateId;
		campaign.createdBy = createdBy;
		campaign.createdAt = campaign.updatedAt = Instant.now();
		return campaign;
	}

    public java.util.List<UUID> getAttachmentIds(){return java.util.List.copyOf(attachmentIds);}
    public void setAttachmentIds(java.util.List<UUID> ids){attachmentIds=ids==null?new java.util.ArrayList<>():new java.util.ArrayList<>(ids);touch();}

	public void setCreatedBy(UUID createdBy) {
		this.createdBy = createdBy;
		touch();
	}

	public void queue() {
		if (!"DRAFT".equals(status)) {
			throw new IllegalStateException("Only draft campaigns can be sent");
		}
		this.status = "QUEUED";
		this.scheduledAt = null;
		touch();
	}

	public void schedule(Instant at, String timezone) {
		if (!"DRAFT".equals(status) && !"SCHEDULED".equals(status)) {
			throw new IllegalStateException("Only draft or scheduled campaigns can be scheduled");
		}
		this.status = "SCHEDULED";
		this.scheduledAt = at;
		this.timezone = timezone;
		touch();
	}

	public void markProcessing() {
		if (!"QUEUED".equals(status) && !"SCHEDULED".equals(status) && !"PROCESSING".equals(status)) {
			throw new IllegalStateException("Campaign is not ready for processing");
		}
		this.status = "PROCESSING";
		touch();
	}

	public void markQueued() {
		if (!"PROCESSING".equals(status)) {
			throw new IllegalStateException("Only processing campaigns can return to the queue");
		}
		this.status = "QUEUED";
		touch();
	}

	public void resume() {
		if (!"STOPPED".equals(status)) {
			throw new IllegalStateException("Only stopped campaigns can be resumed");
		}
		this.status = "QUEUED";
		this.scheduledAt = null;
		touch();
	}

	public void fail() {
		if (!"PROCESSING".equals(status)) {
			throw new IllegalStateException("Only processing campaigns can fail");
		}
		this.status = "FAILED";
		touch();
	}

	public void complete(boolean partial) {
		if (!"PROCESSING".equals(status)) {
			throw new IllegalStateException("Only processing campaigns can be completed");
		}
		this.status = partial ? "PARTIALLY_COMPLETED" : "COMPLETED";
		touch();
	}

	public void stop() {
		if (!"SCHEDULED".equals(status) && !"QUEUED".equals(status) && !"PROCESSING".equals(status)) {
			throw new IllegalStateException("Campaign cannot be stopped in its current state");
		}
		this.status = "STOPPED";
		touch();
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public UUID getDispatchLeaseId() { return dispatchLeaseId; }
    public void edit(String name, String sender, String replyTo, String subject, String preheader,
            String html, String text, UUID templateId) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only drafts can be edited");
        this.templateVersion = null;
        this.name = name; this.sender = sender; this.replyTo = replyTo; this.subject = subject;
        this.preheader = preheader; this.htmlBody = html; this.textBody = text; this.templateId = templateId;
        touch();
    }
    public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getName() { return name; }
	public String getStatus() { return status; }
	public String getSender() { return sender; }
	public String getReplyTo() { return replyTo; }
	public String getSubject() { return subject; }
	public String getPreheader() { return preheader; }
	public String getHtmlBody() { return htmlBody; }
	public String getTextBody() { return textBody; }
	public Integer getTemplateVersion() { return templateVersion; }
    public void pinTemplateVersion(int version) { this.templateVersion = version; }
    public UUID getTemplateId() { return templateId; }
	public Instant getScheduledAt() { return scheduledAt; }
	public String getTimezone() { return timezone; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
