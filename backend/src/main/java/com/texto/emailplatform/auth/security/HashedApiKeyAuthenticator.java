package com.texto.emailplatform.auth.security;

import com.texto.emailplatform.auth.domain.ApiKeyEntity;
import com.texto.emailplatform.auth.domain.ApiKeyRepository;
import com.texto.emailplatform.tenant.domain.TenantEntity;
import com.texto.emailplatform.tenant.domain.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HashedApiKeyAuthenticator implements ApiKeyAuthenticator {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;

    public HashedApiKeyAuthenticator(ApiKeyRepository apiKeyRepository, TenantRepository tenantRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256(rawApiKey.trim());
        return apiKeyRepository.findByKeyHashAndStatus(hash, ApiKeyEntity.STATUS_ACTIVE)
                .filter(ApiKeyEntity::isActive)
            .filter(entity -> tenantRepository.findById(entity.getTenantId())
                .filter(tenant -> TenantEntity.STATUS_ACTIVE.equals(tenant.getStatus()))
                .isPresent())
                .map(entity -> {
                    if (entity.getLastUsedAt() == null || entity.getLastUsedAt().isBefore(java.time.Instant.now().minusSeconds(300))) {
                        entity.markUsed();
                        apiKeyRepository.save(entity);
                    }
                    return toPrincipal(entity);
                });
    }

    private ApiKeyPrincipal toPrincipal(ApiKeyEntity entity) {
        return new ApiKeyPrincipal(entity.getId(), entity.getTenantId(), entity.getKeyPrefix(), entity.getEnvironment(), entity.getScopes());
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
