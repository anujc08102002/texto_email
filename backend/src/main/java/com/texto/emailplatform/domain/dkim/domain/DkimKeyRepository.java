package com.texto.emailplatform.domain.dkim.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DkimKeyRepository extends JpaRepository<DkimKeyEntity, UUID> {

    Optional<DkimKeyEntity> findFirstByDomainIdAndStatusOrderByCreatedAtDesc(UUID domainId, String status);

    Optional<DkimKeyEntity> findByDomainIdAndSelectorAndStatus(UUID domainId, String selector, String status);

    boolean existsByDomainId(UUID domainId);
}
