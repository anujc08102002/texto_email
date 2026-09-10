package com.texto.emailplatform.domain.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainRepository extends JpaRepository<DomainEntity, UUID> {

    List<DomainEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<DomainEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DomainEntity> findByTenantIdAndDomain(UUID tenantId, String domain);

    boolean existsByTenantIdAndDomain(UUID tenantId, String domain);

    long countByTenantId(UUID tenantId);

    @Query("select count(d) from DomainEntity d where d.tenantId = :tenantId and lower(d.domain) not like :suffix")
    long countCustomDomains(@Param("tenantId") UUID tenantId, @Param("suffix") String suffix);
}
