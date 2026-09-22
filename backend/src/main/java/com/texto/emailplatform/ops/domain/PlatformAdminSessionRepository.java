package com.texto.emailplatform.ops.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminSessionRepository extends JpaRepository<PlatformAdminSessionEntity, UUID> {

    Optional<PlatformAdminSessionEntity> findByTokenHash(String tokenHash);

    long deleteByTokenHash(String tokenHash);
}
