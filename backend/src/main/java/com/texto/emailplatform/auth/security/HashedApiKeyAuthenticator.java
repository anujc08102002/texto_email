package com.texto.emailplatform.auth.security;

import com.texto.emailplatform.auth.domain.ApiKeyEntity;
import com.texto.emailplatform.auth.domain.ApiKeyRepository;
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

    public HashedApiKeyAuthenticator(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKeyPrincipal> authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256(rawApiKey.trim());
        return apiKeyRepository.findByKeyHashAndStatus(hash, ApiKeyEntity.STATUS_ACTIVE)
                .filter(ApiKeyEntity::isActive)
                .map(this::toPrincipal);
    }

    private ApiKeyPrincipal toPrincipal(ApiKeyEntity entity) {
        return new ApiKeyPrincipal(entity.getId(), entity.getTenantId(), entity.getKeyPrefix());
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
