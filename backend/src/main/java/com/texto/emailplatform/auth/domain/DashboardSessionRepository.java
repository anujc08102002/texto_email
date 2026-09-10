package com.texto.emailplatform.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardSessionRepository extends JpaRepository<DashboardSessionEntity, UUID> {

    Optional<DashboardSessionEntity> findByTokenHash(String tokenHash);

    long deleteByTokenHash(String tokenHash);

    long deleteByUserId(UUID userId);
}
