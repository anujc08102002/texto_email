package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.segment.SegmentConditionQueryBuilder;
import com.texto.emailplatform.segment.domain.SegmentRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Snapshots eligible contacts in the database, without loading the audience into application memory. */
@Service
public class CampaignAudienceService {
    private final CampaignRepository campaigns;
    private final SegmentRepository segments;
    private final SegmentConditionQueryBuilder conditions;
    private final CampaignAuthorizationService authorization;
    private final EntityManager em;

    public CampaignAudienceService(CampaignRepository campaigns, SegmentRepository segments,
            SegmentConditionQueryBuilder conditions, CampaignAuthorizationService authorization, EntityManager em) {
        this.campaigns = campaigns;
        this.segments = segments;
        this.conditions = conditions;
        this.authorization = authorization;
        this.em = em;
    }

    @Transactional
    public AudienceResult add(UUID campaignId, AudienceRequest request) {
        authorization.requireSender();
        UUID tenant = TenantContext.get().orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required"));
        var campaign = campaigns.lockByIdAndTenantId(campaignId, tenant)
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new ApiException(409, "CAMPAIGN_NOT_DRAFT", "Audience can only be changed in a draft");
        }
        if ((request.segmentId() != null) == Boolean.TRUE.equals(request.allSubscribed())) {
            throw new ApiException(400, "INVALID_AUDIENCE", "Choose a saved segment or all subscribed contacts");
        }
        var predicate = request.segmentId() == null ? null : conditions.build(segments
                .findByIdAndTenantId(request.segmentId(), tenant)
                .orElseThrow(() -> new ApiException(404, "SEGMENT_NOT_FOUND", "Segment was not found"))
                .getDefinition());
        String sql = """
                INSERT INTO campaign_recipients
                    (id, campaign_id, tenant_id, contact_id, email, status, variables, created_at, updated_at)
                SELECT gen_random_uuid(), :campaignId, c.tenant_id, c.id, c.email, 'PENDING',
                    c.properties || jsonb_build_object('email', c.email, 'first_name', c.first_name,
                        'last_name', c.last_name, 'company', c.company, 'phone', c.phone, 'custom', c.properties),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM contacts c
                WHERE c.tenant_id = :tenantId AND c.status = 'ACTIVE' AND c.subscription_status = 'SUBSCRIBED'
                    AND NOT EXISTS (SELECT 1 FROM suppressions s WHERE s.tenant_id = c.tenant_id AND s.normalized_email = c.email)
                """ + (predicate == null ? "" : " AND " + predicate.sql())
                + " ON CONFLICT (campaign_id, email) DO NOTHING";
        var query = em.createNativeQuery(sql).setParameter("campaignId", campaignId).setParameter("tenantId", tenant);
        if (predicate != null) predicate.parameters().forEach(query::setParameter);
        return new AudienceResult(query.executeUpdate());
    }

    public record AudienceRequest(UUID segmentId, Boolean allSubscribed) {}
    public record AudienceResult(int added) {}
}
