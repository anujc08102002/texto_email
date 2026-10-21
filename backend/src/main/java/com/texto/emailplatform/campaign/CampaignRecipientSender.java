package com.texto.emailplatform.campaign;

import com.texto.emailplatform.auth.domain.UserEntity;
import com.texto.emailplatform.auth.domain.UserRepository;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientRepository;
import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.RolePermissions;
import com.texto.emailplatform.contact.domain.ContactEntity;
import com.texto.emailplatform.contact.domain.ContactRepository;
import com.texto.emailplatform.email.EmailService;
import com.texto.emailplatform.email.api.EmailMessageResponse;
import com.texto.emailplatform.email.api.SendEmailRequest;
import com.texto.emailplatform.template.TemplateService;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignRecipientSender {
    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final EmailService emails;
    private final TemplateService templates;
    private final CampaignPersonalizer personalizer;
    private final CampaignUnsubscribeService unsubscribe;

    public CampaignRecipientSender(
            CampaignRepository campaigns,
            CampaignRecipientRepository recipients,
            ContactRepository contacts,
            UserRepository users,
            EmailService emails,
            TemplateService templates,
            CampaignPersonalizer personalizer,
            CampaignUnsubscribeService unsubscribe
    ) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.contacts = contacts;
        this.users = users;
        this.emails = emails;
        this.templates = templates;
        this.personalizer = personalizer;
        this.unsubscribe = unsubscribe;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(UUID campaignId, UUID tenantId, UUID recipientId) {
        CampaignEntity campaign = campaigns.lockByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
        CampaignRecipientEntity recipient = recipients
                .findByIdAndCampaignIdAndTenantId(recipientId, campaignId, tenantId)
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_RECIPIENT_NOT_FOUND", "Campaign recipient was not found"));
        if (!"PENDING".equals(recipient.getStatus())) return;
        if (!"PROCESSING".equals(campaign.getStatus())) return;
        ContactEntity currentContact = recipient.getContactId() == null ? null
                : contacts.findByIdAndTenantId(recipient.getContactId(), tenantId).orElse(null);
        if (currentContact == null || !"ACTIVE".equals(currentContact.getStatus())
                || !"SUBSCRIBED".equals(currentContact.getSubscriptionStatus())) {
            recipient.markSuppressed(null);
            return;
        }

        UserEntity creator = users.findByIdAndTenantId(campaign.getCreatedBy(), tenantId)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .filter(user -> RolePermissions.has(user.getRole(), Permission.EMAIL_SEND))
                .orElseThrow(() -> new ApiException(403, "CAMPAIGN_CREATOR_CANNOT_SEND", "Campaign creator no longer has permission to send"));

        UUID previousTenant = TenantContext.get().orElse(null);
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        TenantContext.set(tenantId);
        setDashboardAuthentication(creator);
        try {
            Map<String, Object> variables = variables(recipient, tenantId);
                Map<String, Object> templateVariables = campaign.getTemplateId() == null
                    ? variables
                    : templateVariables(campaign, tenantId, variables);
            String subject = campaign.getTemplateId() == null
                    ? personalizer.render(campaign.getSubject(), variables, false)
                    : campaign.getSubject();
            String html = campaign.getTemplateId() == null
                    ? personalizer.render(campaign.getHtmlBody(), variables, true)
                    : campaign.getHtmlBody();
            String text = campaign.getTemplateId() == null
                    ? personalizer.render(campaign.getTextBody(), variables, false)
                    : campaign.getTextBody();

            EmailMessageResponse message = emails.send(
                    new SendEmailRequest(
                            campaign.getSender(),
                            List.of(recipient.getEmail()),
                            List.of(),
                            List.of(),
                            campaign.getReplyTo(),
                            subject,
                            html,
                            text,
                            campaign.getTemplateId(),
                            campaign.getTemplateVersion(),
                            templateVariables,
                            Map.of("campaignId", campaignId.toString(), "campaignRecipientId", recipientId.toString(),
                                    "campaignUnsubscribeUrl", unsubscribe.urlFor(tenantId, recipient.getEmail()),
                                    "attachmentIds", campaign.getAttachmentIds().stream().map(UUID::toString).toList())
                    ),
                    "campaign:" + campaignId + ":recipient:" + recipientId
            );

            if ("SUPPRESSED".equals(message.status())) {
                recipient.markSuppressed(message.id());
            } else {
                recipient.markAccepted(message.id());
            }
        } finally {
            if (previousTenant == null) TenantContext.clear();
            else TenantContext.set(previousTenant);
            restoreAuthentication(previousAuthentication);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID campaignId, UUID tenantId, UUID recipientId, String error) {
        recipients.findByIdAndCampaignIdAndTenantId(recipientId, campaignId, tenantId)
                .filter(recipient -> "PENDING".equals(recipient.getStatus()))
                .ifPresent(recipient -> recipient.markFailed(error));
    }

        private Map<String, Object> templateVariables(
            CampaignEntity campaign,
            UUID tenantId,
            Map<String, Object> variables
        ) {
        var version = templates.requireRenderableVersion(tenantId, campaign.getTemplateId(), campaign.getTemplateVersion());
        return version.getVariablesSchema().keySet().stream()
            .filter(name -> variables.get(name) != null)
            .collect(Collectors.toMap(
                name -> name,
                variables::get,
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
        }

    private Map<String, Object> variables(CampaignRecipientEntity recipient, UUID tenantId) {
        Map<String, Object> values = new LinkedHashMap<>(recipient.getVariables());
        values.putIfAbsent("email", recipient.getEmail());
        ContactEntity contact = recipient.getContactId() == null
                ? null
                : contacts.findByIdAndTenantId(recipient.getContactId(), tenantId).orElse(null);
        Map<String, Object> custom = customProperties(contact);
        values.put("custom", custom);
        if (contact != null) {
            values.putIfAbsent("first_name", contact.getFirstName());
            values.putIfAbsent("last_name", contact.getLastName());
            values.putIfAbsent("company", contact.getCompany());
            values.putIfAbsent("phone", contact.getPhone());
            custom.forEach(values::putIfAbsent);
        }
        return values;
    }

    private Map<String, Object> customProperties(ContactEntity contact) {
        return contact == null ? Map.of() : new LinkedHashMap<>(contact.getProperties());
    }

    private static void setDashboardAuthentication(UserEntity creator) {
        DashboardPrincipal principal = new DashboardPrincipal(
                creator.getId(),
                creator.getTenantId(),
                creator.getEmail(),
                creator.getRole()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                "campaign-dispatch",
                List.of(new SimpleGrantedAuthority("ROLE_DASHBOARD"))
        ));
        SecurityContextHolder.setContext(context);
    }

    private static void restoreAuthentication(Authentication authentication) {
        if (authentication == null) SecurityContextHolder.clearContext();
        else SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}