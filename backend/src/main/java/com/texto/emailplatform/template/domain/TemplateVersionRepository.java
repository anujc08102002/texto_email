package com.texto.emailplatform.template.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersionEntity, UUID> {

    List<TemplateVersionEntity> findByTemplateIdOrderByVersionDesc(UUID templateId);

    Optional<TemplateVersionEntity> findByTemplateIdAndVersion(UUID templateId, int version);

    Optional<TemplateVersionEntity> findByIdAndTemplateId(UUID id, UUID templateId);

    Optional<TemplateVersionEntity> findTopByTemplateIdOrderByVersionDesc(UUID templateId);
}
