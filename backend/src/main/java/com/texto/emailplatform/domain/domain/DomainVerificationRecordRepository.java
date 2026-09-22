package com.texto.emailplatform.domain.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainVerificationRecordRepository extends JpaRepository<DomainVerificationRecordEntity, UUID> {

    List<DomainVerificationRecordEntity> findByDomainIdOrderByTypeAsc(UUID domainId);

    List<DomainVerificationRecordEntity> findByDomainIdInOrderByTypeAsc(List<UUID> domainIds);

    void deleteByDomainId(UUID domainId);
}
