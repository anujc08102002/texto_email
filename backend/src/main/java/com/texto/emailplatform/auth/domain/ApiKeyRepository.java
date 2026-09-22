package com.texto.emailplatform.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID>, JpaSpecificationExecutor<ApiKeyEntity> {

    Optional<ApiKeyEntity> findByKeyHashAndStatus(String keyHash, String status);

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ApiKeyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
