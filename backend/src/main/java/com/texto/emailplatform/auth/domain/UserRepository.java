package com.texto.emailplatform.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<UserEntity> findFirstByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
