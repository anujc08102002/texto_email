package com.texto.emailplatform.ops.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdminEntity, UUID> {

    Optional<PlatformAdminEntity> findFirstByEmailIgnoreCase(String email);

    long countByStatus(String status);
}
