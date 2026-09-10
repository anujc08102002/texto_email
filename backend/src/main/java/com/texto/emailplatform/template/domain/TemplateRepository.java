package com.texto.emailplatform.template.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<TemplateEntity, UUID> {

    List<TemplateEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    long countByTenantIdAndStatusNot(UUID tenantId, String status);
}
