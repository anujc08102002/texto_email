package com.texto.emailplatform.campaign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.campaign.api.CampaignPageResponse;
import com.texto.emailplatform.campaign.api.CampaignRecipientRequest;
import com.texto.emailplatform.campaign.api.CampaignRecipientResponse;
import com.texto.emailplatform.campaign.api.CampaignReportResponse;
import com.texto.emailplatform.campaign.api.CampaignRecipientPageResponse;
import com.texto.emailplatform.campaign.api.CampaignResponse;
import com.texto.emailplatform.campaign.api.SaveCampaignDraftRequest;
import com.texto.emailplatform.campaign.api.ScheduleCampaignRequest;
import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientRepository;
import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.campaign.domain.CampaignScheduleEntity;
import com.texto.emailplatform.campaign.domain.CampaignScheduleRepository;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.contact.domain.ContactEntity;
import com.texto.emailplatform.contact.domain.ContactRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
	private final CampaignRepository campaigns;
	private final CampaignRecipientRepository recipients;
	private final CampaignScheduleRepository schedules;
	private final ContactRepository contacts;
	private final ObjectMapper objectMapper;
	private final CampaignAuthorizationService authorization;
    private final CampaignReadinessService readiness;
    private final com.texto.emailplatform.attachment.AttachmentService attachments;

	public CampaignService(
			CampaignRepository campaigns,
			CampaignRecipientRepository recipients,
			CampaignScheduleRepository schedules,
			ContactRepository contacts,
			ObjectMapper objectMapper,
			CampaignAuthorizationService authorization,
            CampaignReadinessService readiness,
            com.texto.emailplatform.attachment.AttachmentService attachments
	) {
		this.campaigns = campaigns;
		this.recipients = recipients;
		this.schedules = schedules;
		this.contacts = contacts;
		this.objectMapper = objectMapper;
		this.authorization = authorization;
        this.readiness = readiness;
        this.attachments = attachments;
	}

	@Transactional
	public CampaignResponse saveDraft(SaveCampaignDraftRequest request) {
		var principal = authorization.requireSender();
		String name = request.name().trim();
		if (name.isEmpty()) {
			throw new ApiException(400, "VALIDATION_ERROR", "Campaign name is required");
		}
		CampaignEntity campaign = CampaignEntity.draft(
				tenant(),
				name,
				blank(request.sender()),
				blank(request.replyTo()),
				blank(request.subject()),
				blank(request.preheader()),
				request.htmlBody(),
				request.textBody(),
				request.templateId(),
				principal.userId()
		);
		attachments.validate(tenant(),request.attachmentIds());
        campaign.setAttachmentIds(request.attachmentIds());
		return response(campaigns.save(campaign));
	}

    @Transactional
    public CampaignResponse cloneDraft(UUID id) {
        var c = requireOwned(id);
        String name = c.getName().substring(0, Math.min(c.getName().length(), 153)) + " (copy)";
        return saveDraft(new SaveCampaignDraftRequest(name, c.getSender(), c.getReplyTo(), c.getSubject(),
                c.getPreheader(), c.getHtmlBody(), c.getTextBody(), c.getTemplateId(), c.getAttachmentIds()));
    }

    @Transactional
    public void removeRecipient(UUID campaignId, UUID recipientId) {
        authorization.requireSender();
        requireDraft(campaignId);
        var recipient = recipients.findByIdAndCampaignIdAndTenantId(recipientId, campaignId, tenant())
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_RECIPIENT_NOT_FOUND", "Campaign recipient was not found"));
        recipients.delete(recipient);
    }

    @Transactional(readOnly = true)
    public SaveCampaignDraftRequest content(UUID id) {
        var c = requireOwned(id);
        return new SaveCampaignDraftRequest(c.getName(), c.getSender(), c.getReplyTo(), c.getSubject(),
                c.getPreheader(), c.getHtmlBody(), c.getTextBody(), c.getTemplateId(), c.getAttachmentIds());
    }

    @Transactional
    public CampaignResponse updateDraft(UUID id, SaveCampaignDraftRequest request) {
        authorization.requireSender();
        var c = requireDraft(id);
        c.edit(request.name().trim(), blank(request.sender()), blank(request.replyTo()), blank(request.subject()),
                blank(request.preheader()), request.htmlBody(), request.textBody(), request.templateId());
        attachments.validate(tenant(),request.attachmentIds());
        c.setAttachmentIds(request.attachmentIds());
        return response(c);
    }

	@Transactional(readOnly = true)
	public CampaignPageResponse list(int page, int size) {
		return list(page, size, "", "");
	    }

	    @Transactional(readOnly = true)
	    public CampaignPageResponse list(int page, int size, String query, String status) {
		return list(page,size,query,status,"updatedAt","desc");
    }

    @Transactional(readOnly = true)
    public CampaignPageResponse list(int page,int size,String query,String status,String sort,String direction) {
        String safeQuery = blank(query) == null ? "" : blank(query);
		String safeStatus = blank(status) == null ? "" : blank(status).toUpperCase(java.util.Locale.ROOT);
		Page<CampaignEntity> result = campaigns.searchByTenant(
			tenant(),
			safeStatus,
			safeQuery,
			PageRequest.of(
				Math.max(0, page),
				Math.min(100, Math.max(1, size)),
				com.texto.emailplatform.common.PageSort.of(sort,direction,java.util.Set.of("name","status","subject","scheduledAt","updatedAt","createdAt"))
			)
		);
		return new CampaignPageResponse(
				result.map(CampaignService::response).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public CampaignResponse get(UUID id) {
		return response(requireOwned(id));
	}

	@Transactional
	public List<CampaignRecipientResponse> addRecipients(UUID campaignId, CampaignRecipientRequest request) {
		authorization.requireSender();
		CampaignEntity campaign = requireDraft(campaignId);
		UUID tenantId = tenant();
		List<CampaignRecipientResponse> added = new ArrayList<>();
		for (UUID contactId : new LinkedHashSet<>(request.contactIds())) {
			ContactEntity contact = contacts.findByIdAndTenantId(contactId, tenantId)
					.orElseThrow(() -> new ApiException(404, "CONTACT_NOT_FOUND", "Contact was not found"));
			if (!"ACTIVE".equals(contact.getStatus()) || !"SUBSCRIBED".equals(contact.getSubscriptionStatus())) {
				continue;
			}
			if (recipients.existsByCampaignIdAndEmail(campaign.getId(), contact.getEmail())) {
				continue;
			}
			added.add(recipient(recipients.save(CampaignRecipientEntity.queued(
					campaign.getId(),
					tenantId,
					contact.getId(),
					contact.getEmail(),
					variables(contact)
			))));
		}
		return added;
	}

	@Transactional(readOnly = true)
	    public CampaignRecipientPageResponse listRecipients(UUID campaignId, int page, int size) {
		CampaignEntity campaign = requireOwned(campaignId);
		Page<CampaignRecipientEntity> result = recipients.findByCampaignIdAndTenantIdOrderByCreatedAtAsc(
			campaign.getId(),
			tenant(),
			PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)))
		);
		return new CampaignRecipientPageResponse(
			result.map(item -> recipient(item, recipients.effectiveState(item.getId(), campaign.getId(), tenant()))).toList(),
			result.getNumber(),
			result.getSize(),
			result.getTotalElements(),
			result.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public CampaignReportResponse report(UUID id) {
		CampaignEntity campaign = requireOwned(id);
		UUID tenantId = tenant();
		return new CampaignReportResponse(
				campaign.getId(),
				recipients.countByCampaignIdAndTenantId(campaign.getId(), tenantId),
				effectiveCount(campaign, tenantId, "PENDING"),
				effectiveCount(campaign, tenantId, "QUEUED"),
				effectiveCount(campaign, tenantId, "ACCEPTED"),
				effectiveCount(campaign, tenantId, "DELIVERED"),
				effectiveCount(campaign, tenantId, "BOUNCED"),
				effectiveCount(campaign, tenantId, "COMPLAINED"),
				effectiveCount(campaign, tenantId, "FAILED"),
				effectiveCount(campaign, tenantId, "SUPPRESSED")
		);
	}

	@Transactional
	public CampaignResponse schedule(UUID id, ScheduleCampaignRequest request) {
		authorization.requireSender();
		CampaignEntity campaign = requireDraft(id);
        readiness.validate(campaign);
        CampaignReadinessService.validateTimezone(request.timezone());
		requireRecipients(campaign);
		schedules.invalidateActive(campaign.getId());
		campaign.schedule(request.scheduledAt(), request.timezone().trim());
		schedules.save(CampaignScheduleEntity.active(
				campaign.getId(), tenant(), request.scheduledAt(), request.timezone().trim()
		));
		return response(campaign);
	}

	@Transactional
	public CampaignResponse stop(UUID id) {
		authorization.requireSender();
		CampaignEntity campaign = requireLocked(id);
		try {
			campaign.stop();
		} catch (IllegalStateException exception) {
			throw new ApiException(409, "CAMPAIGN_NOT_STOPPABLE", exception.getMessage());
		}
		schedules.invalidateActive(campaign.getId());
		return response(campaign);
	}

	private void requireRecipients(CampaignEntity campaign) {
		if (recipients.countByCampaignIdAndTenantId(campaign.getId(), campaign.getTenantId()) == 0) {
			throw new ApiException(409, "CAMPAIGN_HAS_NO_RECIPIENTS", "Add at least one eligible recipient before scheduling");
		}
	}

	private CampaignEntity requireDraft(UUID id) {
		CampaignEntity campaign = requireLocked(id);
		if (!"DRAFT".equals(campaign.getStatus())) {
			throw new ApiException(409, "CAMPAIGN_NOT_DRAFT", "Recipients can only be changed while the campaign is a draft");
		}
		return campaign;
	}

    private CampaignEntity requireLocked(UUID id) {
        return campaigns.lockByIdAndTenantId(id, tenant())
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
    }

	private CampaignEntity requireOwned(UUID id) {
		return campaigns.findById(id)
				.filter(campaign -> campaign.getTenantId().equals(tenant()))
				.orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
	}

	private long effectiveCount(CampaignEntity campaign, UUID tenantId, String state) {
		return recipients.countEffectiveState(campaign.getId(), tenantId, state);
	}

	private Map<String, Object> variables(ContactEntity contact) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("email", contact.getEmail());
		values.put("first_name", contact.getFirstName());
		values.put("last_name", contact.getLastName());
		values.put("company", contact.getCompany());
		values.put("phone", contact.getPhone());
		Map<String, Object> custom = new LinkedHashMap<>(contact.getProperties());
		custom.forEach((key, value) -> {
			if (key.matches("[A-Za-z0-9_]+")) values.putIfAbsent(key, value);
		});
		values.put("custom", custom);
		return values;
	}

	private static CampaignResponse response(CampaignEntity campaign) {
		return new CampaignResponse(
				campaign.getId(), campaign.getName(), campaign.getStatus(), campaign.getSender(),
				campaign.getReplyTo(), campaign.getSubject(), campaign.getPreheader(),
				campaign.getTemplateId(), campaign.getScheduledAt(), campaign.getTimezone(),
				campaign.getCreatedAt(), campaign.getUpdatedAt()
		);
	}

	private CampaignRecipientResponse recipient(CampaignRecipientEntity recipient) { return recipient(recipient, recipient.getStatus()); }

    private CampaignRecipientResponse recipient(CampaignRecipientEntity recipient, String state) {
		try {
			return new CampaignRecipientResponse(
					recipient.getId(), recipient.getContactId(), recipient.getEmail(), state,
					objectMapper.writeValueAsString(recipient.getVariables()), recipient.getErrorMessage(), recipient.getCreatedAt()
			);
		} catch (Exception exception) {
			throw new ApiException(500, "CAMPAIGN_VARIABLES_ERROR", "Unable to serialize campaign variables");
		}
	}

	private static UUID tenant() {
		return TenantContext.get().orElseThrow(
				() -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required")
		);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
