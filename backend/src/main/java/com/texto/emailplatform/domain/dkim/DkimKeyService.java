package com.texto.emailplatform.domain.dkim;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.domain.dkim.domain.DkimKeyEntity;
import com.texto.emailplatform.domain.dkim.domain.DkimKeyRepository;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped retrieval of DKIM signing keys for the internal signing boundary.
 *
 * <p>Callers MUST provide the owning tenant id; the domain is resolved with tenant scoping so a
 * tenant can never obtain another tenant's private key. The reconstructed {@link PrivateKey} is
 * returned only to trusted internal callers (the future DKIM signer) and is never exposed via any
 * API, serialized, or logged.
 */
@Service
public class DkimKeyService {

    private final DomainRepository domainRepository;
    private final DkimKeyRepository dkimKeyRepository;
    private final DkimKeyProtector dkimKeyProtector;

    public DkimKeyService(
            DomainRepository domainRepository,
            DkimKeyRepository dkimKeyRepository,
            DkimKeyProtector dkimKeyProtector
    ) {
        this.domainRepository = domainRepository;
        this.dkimKeyRepository = dkimKeyRepository;
        this.dkimKeyProtector = dkimKeyProtector;
    }

    /**
     * Resolves the active DKIM signing key for a tenant's domain, decrypting and reconstructing the
     * private key. Throws if the domain does not belong to the tenant or has no active key.
     */
    @Transactional(readOnly = true)
    public DkimSigningKey getSigningKey(UUID tenantId, UUID domainId) {
        DomainEntity domain = domainRepository.findByIdAndTenantId(domainId, tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "DOMAIN_NOT_FOUND",
                        "Domain was not found"
                ));

        DkimKeyEntity key = dkimKeyRepository
                .findFirstByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), DkimKeyEntity.STATUS_ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT.value(),
                        "DKIM_KEY_NOT_FOUND",
                        "No active DKIM signing key is provisioned for this domain"
                ));

        return toSigningKey(domain.getId(), key);
    }

    /**
     * Resolves the active DKIM signing key for a tenant's <b>verified</b> sending domain, if one is
     * configured. Returns empty (rather than throwing) when the domain is not a verified custom
     * domain of the tenant or has no active key — e.g. platform test senders ({@code *.texto.test}),
     * which are simply sent unsigned. Never returns a key for another tenant's domain.
     */
    @Transactional(readOnly = true)
    public Optional<DkimSigningKey> findActiveSigningKey(UUID tenantId, String senderDomain) {
        if (tenantId == null || senderDomain == null || senderDomain.isBlank()) {
            return Optional.empty();
        }
        return domainRepository.findByTenantIdAndDomain(tenantId, senderDomain)
                .filter(domain -> DomainEntity.STATUS_VERIFIED.equals(domain.getStatus()))
                .flatMap(domain -> dkimKeyRepository
                        .findFirstByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), DkimKeyEntity.STATUS_ACTIVE)
                        .map(key -> toSigningKey(domain.getId(), key)));
    }

    private DkimSigningKey toSigningKey(UUID domainId, DkimKeyEntity key) {
        byte[] pkcs8 = dkimKeyProtector.decryptPrivateKey(key.getEncryptedPrivateKey());
        PrivateKey privateKey = reconstructPrivateKey(key.getAlgorithm(), pkcs8);
        return new DkimSigningKey(domainId, key.getSelector(), key.getAlgorithm(), privateKey);
    }

    private static PrivateKey reconstructPrivateKey(String algorithm, byte[] pkcs8) {
        try {
            String jcaAlgorithm = "rsa".equalsIgnoreCase(algorithm) ? "RSA" : algorithm.toUpperCase();
            KeyFactory keyFactory = KeyFactory.getInstance(jcaAlgorithm);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to reconstruct DKIM private key", exception);
        }
    }
}
