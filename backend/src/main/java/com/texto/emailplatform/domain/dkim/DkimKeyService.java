package com.texto.emailplatform.domain.dkim;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.domain.DkimKeyMaterial;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;
import java.security.PrivateKey;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant/domain-scoped DKIM key custody. Decrypts only for signing.
 */
@Service
public class DkimKeyService {

    public static final String KEY_REF_PREFIX = "dkim-key:";

    private final DkimKeyRepository dkimKeyRepository;
    private final DomainRepository domainRepository;
    private final DomainVerificationRecordRepository verificationRecordRepository;
    private final DkimKeyProtector protector;

    public DkimKeyService(
            DkimKeyRepository dkimKeyRepository,
            DomainRepository domainRepository,
            DomainVerificationRecordRepository verificationRecordRepository,
            DkimKeyProtector protector
    ) {
        this.dkimKeyRepository = dkimKeyRepository;
        this.domainRepository = domainRepository;
        this.verificationRecordRepository = verificationRecordRepository;
        this.protector = protector;
    }

    @Transactional
    public DkimKeyEntity ensureActiveKey(DomainEntity domain, String preferredSelector) {
        Optional<DkimKeyEntity> existing = dkimKeyRepository.findByDomainIdAndStatus(
                domain.getId(),
                DkimKeyEntity.STATUS_ACTIVE
        );
        if (existing.isPresent()) {
            return existing.get();
        }
        DomainVerificationRecordEntity dkimRecord = dkimRecord(domain.getId()).orElse(null);
        if (dkimRecord != null && DkimKeyMaterial.isStoredPrivateKey(dkimRecord.getPrivateKeyRef())) {
            String selector = firstNonBlank(dkimRecord.getSelector(), preferredSelector, "texto");
            DkimKeyEntity migrated = persistActive(
                    domain.getId(),
                    selector,
                    dkimRecord.getPublicKey(),
                    DkimKeyMaterial.pkcs8Der(dkimRecord.getPrivateKeyRef())
            );
            dkimRecord.assignKeyRef(reference(migrated.getId()));
            verificationRecordRepository.save(dkimRecord);
            return migrated;
        }
        DkimKeyMaterial.Generated generated = DkimKeyMaterial.generate();
        String selector = firstNonBlank(
                dkimRecord == null ? null : dkimRecord.getSelector(),
                preferredSelector,
                "texto"
        );
        return persistActive(domain.getId(), selector, generated.publicKeyPkcs1(), generated.pkcs8Der());
    }

    @Transactional(readOnly = true)
    public DkimKeyEntity requireActiveKey(UUID tenantId, UUID domainId) {
        DomainEntity domain = domainRepository.findByIdAndTenantId(domainId, tenantId)
                .orElseThrow(DkimKeyService::notFound);
        return dkimKeyRepository.findByDomainIdAndStatus(domain.getId(), DkimKeyEntity.STATUS_ACTIVE)
                .orElseThrow(DkimKeyService::notFound);
    }

    @Transactional
    public DkimSigningMaterial requireSigningMaterial(DomainEntity domain) {
        DkimKeyEntity entity = dkimKeyRepository.findByDomainIdAndStatus(domain.getId(), DkimKeyEntity.STATUS_ACTIVE)
                .orElseGet(() -> ensureActiveKey(domain, null));
        try {
            PrivateKey privateKey = DkimKeyMaterial.loadPkcs8(protector.decrypt(entity.getEncryptedPrivateKey()));
            if (!DkimKeyMaterial.publicMatches(entity.getPublicKey(), privateKey)) {
                throw new IllegalStateException("DKIM key pair mismatch");
            }
            return new DkimSigningMaterial(entity.getSelector(), entity.getPublicKey(), privateKey);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("DKIM signing key is unavailable");
        }
    }

    public static String reference(UUID keyId) {
        return KEY_REF_PREFIX + keyId;
    }

    public static boolean isKeyReference(String value) {
        return value != null && value.startsWith(KEY_REF_PREFIX);
    }

    private DkimKeyEntity persistActive(UUID domainId, String selector, String publicKey, byte[] pkcs8Der) {
        String encrypted = protector.encrypt(pkcs8Der);
        DkimKeyEntity entity = DkimKeyEntity.createActive(
                domainId,
                selector.trim().toLowerCase(Locale.ROOT),
                publicKey,
                encrypted,
                DkimKeyMaterial.RSA_KEY_SIZE
        );
        return dkimKeyRepository.save(entity);
    }

    private Optional<DomainVerificationRecordEntity> dkimRecord(UUID domainId) {
        return verificationRecordRepository.findByDomainIdOrderByTypeAsc(domainId).stream()
                .filter(record -> DomainVerificationRecordEntity.TYPE_DKIM.equals(record.getType()))
                .findFirst();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "texto";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim().toLowerCase(Locale.ROOT);
            }
        }
        return "texto";
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND.value(), "DKIM_KEY_NOT_FOUND", "DKIM key was not found");
    }
}
