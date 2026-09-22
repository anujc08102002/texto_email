package com.texto.emailplatform.ops;

import com.texto.emailplatform.auth.security.HashedApiKeyAuthenticator;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.ops.api.OpsAdminResponse;
import com.texto.emailplatform.ops.api.OpsAuthResponse;
import com.texto.emailplatform.ops.domain.PlatformAdminEntity;
import com.texto.emailplatform.ops.domain.PlatformAdminRepository;
import com.texto.emailplatform.ops.domain.PlatformAdminSessionEntity;
import com.texto.emailplatform.ops.domain.PlatformAdminSessionRepository;
import com.texto.emailplatform.ops.security.PlatformAdminPrincipal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsAuthService {

    static final Duration SESSION_TTL = Duration.ofHours(8);

    private final PlatformAdminRepository adminRepository;
    private final PlatformAdminSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public OpsAuthService(
            PlatformAdminRepository adminRepository,
            PlatformAdminSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.adminRepository = adminRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public OpsAuthResponse login(String email, String password) {
        PlatformAdminEntity admin = adminRepository.findFirstByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(PlatformAdminEntity::isActive)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "AUTHENTICATION_FAILED",
                        "Authentication failed"
                ));
        String token = generateToken();
        sessionRepository.save(PlatformAdminSessionEntity.create(
                admin.getId(),
                HashedApiKeyAuthenticator.sha256(token),
                Instant.now().plus(SESSION_TTL)
        ));
        return new OpsAuthResponse(token, "Bearer", toResponse(admin));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.deleteByTokenHash(HashedApiKeyAuthenticator.sha256(rawToken.trim()));
    }

    @Transactional(readOnly = true)
    public Optional<PlatformAdminPrincipal> authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = HashedApiKeyAuthenticator.sha256(rawToken.trim());
        return sessionRepository.findByTokenHash(hash)
                .filter(session -> !session.isExpired())
                .flatMap(session -> adminRepository.findById(session.getAdminId()))
                .filter(PlatformAdminEntity::isActive)
                .map(admin -> new PlatformAdminPrincipal(admin.getId(), admin.getEmail(), admin.getDisplayName()));
    }

    public OpsAdminResponse current(PlatformAdminPrincipal principal) {
        return new OpsAdminResponse(principal.adminId(), principal.email(), principal.displayName(), "PLATFORM_ADMIN");
    }

    private static OpsAdminResponse toResponse(PlatformAdminEntity admin) {
        return new OpsAdminResponse(admin.getId(), admin.getEmail(), admin.getDisplayName(), "PLATFORM_ADMIN");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "ops_" + HexFormat.of().formatHex(bytes);
    }
}
