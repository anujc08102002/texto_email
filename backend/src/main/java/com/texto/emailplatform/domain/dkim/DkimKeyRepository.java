package com.texto.emailplatform.domain.dkim;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DkimKeyRepository extends JpaRepository<DkimKeyEntity, UUID> {

    Optional<DkimKeyEntity> findByDomainIdAndStatus(UUID domainId, String status);

    Optional<DkimKeyEntity> findByDomainIdAndSelector(UUID domainId, String selector);
}
