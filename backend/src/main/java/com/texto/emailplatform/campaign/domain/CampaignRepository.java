package com.texto.emailplatform.campaign.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CampaignEntity c where c.id = :id and c.tenantId = :tenantId")
    Optional<CampaignEntity> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
    Page<CampaignEntity> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<CampaignEntity> findByIdAndTenantId(UUID id, UUID tenantId);

        @Query("select c from CampaignEntity c where c.tenantId = :tenantId "
            + "and (:status = '' or c.status = :status) "
            + "and (:query = '' or lower(c.name) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(c.subject, '')) like lower(concat('%', :query, '%'))) ")
        Page<CampaignEntity> searchByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("query") String query,
            Pageable pageable
        );

    @Query("select c.id from CampaignEntity c where c.status = 'QUEUED' "
	    + "or (c.status = 'SCHEDULED' and c.scheduledAt <= :now) "
            + "or (c.status = 'PROCESSING' and c.dispatchLeaseUntil < :now) order by c.updatedAt asc")
    List<UUID> findDispatchableIds(@Param("now") Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CampaignEntity c set c.status = 'PROCESSING', c.updatedAt = :now, c.dispatchLeaseId = :leaseId, c.dispatchLeaseUntil = :leaseUntil "
	    + "where c.id = :id and (c.status = 'QUEUED' or (c.status = 'SCHEDULED' and c.scheduledAt <= :now) "
            + "or (c.status = 'PROCESSING' and c.dispatchLeaseUntil < :now))")
    int claimForDispatch(@Param("id") UUID id, @Param("now") Instant now,
            @Param("leaseId") UUID leaseId, @Param("leaseUntil") Instant leaseUntil);
}
