package com.texto.emailplatform.campaign.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipientEntity, UUID> {
    @Query(value = "SELECT " + com.texto.emailplatform.campaign.CampaignRecipientStates.EXPRESSION
            + " FROM campaign_recipients r LEFT JOIN email_messages m ON m.id = r.email_message_id AND m.tenant_id = r.tenant_id"
            + " WHERE r.id = :id AND r.campaign_id = :campaignId AND r.tenant_id = :tenantId", nativeQuery = true)
    String effectiveState(@Param("id") UUID id, @Param("campaignId") UUID campaignId, @Param("tenantId") UUID tenantId);
	boolean existsByCampaignIdAndEmail(UUID campaignId, String email);
	Optional<CampaignRecipientEntity> findByIdAndCampaignIdAndTenantId(UUID id, UUID campaignId, UUID tenantId);
	List<CampaignRecipientEntity> findByCampaignIdAndTenantIdOrderByCreatedAtAsc(UUID campaignId, UUID tenantId);
	    Page<CampaignRecipientEntity> findByCampaignIdAndTenantIdOrderByCreatedAtAsc(
		    UUID campaignId,
		    UUID tenantId,
		    Pageable pageable
	    );
	List<CampaignRecipientEntity> findByCampaignIdAndTenantIdAndStatusOrderByCreatedAtAsc(
			UUID campaignId,
			UUID tenantId,
			String status,
			Pageable pageable
	);
	long countByCampaignIdAndTenantId(UUID campaignId, UUID tenantId);
	long countByCampaignIdAndTenantIdAndStatus(UUID campaignId, UUID tenantId, String status);

		@Query(value = """
						SELECT COUNT(*)
						FROM campaign_recipients r
						LEFT JOIN email_messages m
							ON m.id = r.email_message_id AND m.tenant_id = r.tenant_id
						WHERE r.campaign_id = :campaignId AND r.tenant_id = :tenantId
							AND (
								(:state = 'PENDING' AND r.status = 'PENDING')
								OR (:state = 'QUEUED' AND r.status = 'ACCEPTED' AND m.status IN ('QUEUED','PROCESSING','SENDING','DEFERRED'))
								OR (:state = 'ACCEPTED' AND r.status = 'ACCEPTED' AND m.status = 'DELIVERED')
								OR (:state = 'DELIVERED' AND 1 = 0)
								OR (:state = 'BOUNCED' AND m.status = 'BOUNCED')
								OR (:state = 'COMPLAINED' AND EXISTS (
										SELECT 1 FROM complaint_events ce
										WHERE ce.email_message_id = m.id
											AND ce.tenant_id = r.tenant_id
											AND ce.processing_status = 'APPLIED'
								))
								OR (:state = 'FAILED' AND (r.status = 'FAILED' OR m.status = 'FAILED'))
								OR (:state = 'SUPPRESSED' AND (r.status = 'SUPPRESSED' OR m.status = 'SUPPRESSED'))
							)
						""", nativeQuery = true)
		long countEffectiveState(
						@Param("campaignId") UUID campaignId,
						@Param("tenantId") UUID tenantId,
						@Param("state") String state
		);
}
