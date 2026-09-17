package com.texto.emailplatform.domain.dkim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.delivery.DkimSigner;
import com.texto.emailplatform.domain.DkimKeyMaterial;
import com.texto.emailplatform.domain.dns.DkimDnsRecord;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DkimKeyServiceTest {

    private static final byte[] MASTER_KEY = Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    private final DkimKeyRepository dkimKeyRepository = Mockito.mock(DkimKeyRepository.class);
    private final DomainRepository domainRepository = Mockito.mock(DomainRepository.class);
    private final DomainVerificationRecordRepository verificationRecordRepository =
            Mockito.mock(DomainVerificationRecordRepository.class);
    private final DkimKeyProtector protector = DkimKeyProtector.withRawKey(MASTER_KEY);
    private final AtomicReference<DkimKeyEntity> stored = new AtomicReference<>();
    private DkimKeyService service;

    @BeforeEach
    void setUp() {
        stored.set(null);
        service = new DkimKeyService(dkimKeyRepository, domainRepository, verificationRecordRepository, protector);
        when(dkimKeyRepository.findByDomainIdAndStatus(any(), any())).thenAnswer(invocation -> {
            DkimKeyEntity entity = stored.get();
            String status = invocation.getArgument(1);
            if (entity != null && status.equals(entity.getStatus())) {
                return Optional.of(entity);
            }
            return Optional.empty();
        });
        when(dkimKeyRepository.save(any())).thenAnswer(invocation -> {
            DkimKeyEntity entity = invocation.getArgument(0);
            stored.set(entity);
            return entity;
        });
        when(verificationRecordRepository.findByDomainIdOrderByTypeAsc(any())).thenReturn(List.of());
    }

    @Test
    void generatedKeyPersistsEncryptedPrivateMaterial() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity first = service.ensureActiveKey(domain, "texto");
        DkimKeyEntity again = service.ensureActiveKey(domain, "texto");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(first.getEncryptedPrivateKey()).startsWith("dk1:");
        assertThat(first.getEncryptedPrivateKey()).doesNotStartWith("pkcs8:");
        byte[] recovered = protector.decrypt(first.getEncryptedPrivateKey());
        assertThat(DkimKeyMaterial.publicMatches(first.getPublicKey(), DkimKeyMaterial.loadPkcs8(recovered))).isTrue();
        assertThat(first.getStatus()).isEqualTo(DkimKeyEntity.STATUS_ACTIVE);
        assertThat(first.getSelector()).isEqualTo("texto");
        assertThat(DkimDnsRecord.generate(first.getPublicKey())).isEqualTo("v=DKIM1; k=rsa; p=" + first.getPublicKey());
    }

    @Test
    void inactiveKeyIsNotSelected() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity active = service.ensureActiveKey(domain, "texto");
        active.retire();

        DkimKeyEntity replacement = service.ensureActiveKey(domain, "texto2");
        assertThat(replacement.getId()).isNotEqualTo(active.getId());
        assertThat(replacement.getSelector()).isEqualTo("texto2");
        assertThat(replacement.isActive()).isTrue();
        assertThat(active.getStatus()).isEqualTo(DkimKeyEntity.STATUS_RETIRED);
    }

    @Test
    void tenantCannotRetrieveAnotherTenantsKey() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity key = service.ensureActiveKey(domain, "texto");
        UUID otherTenant = UUID.randomUUID();
        when(domainRepository.findByIdAndTenantId(domain.getId(), otherTenant)).thenReturn(Optional.empty());
        when(domainRepository.findByIdAndTenantId(domain.getId(), domain.getTenantId())).thenReturn(Optional.of(domain));

        assertThat(service.requireActiveKey(domain.getTenantId(), domain.getId()).getId()).isEqualTo(key.getId());
        assertThatThrownBy(() -> service.requireActiveKey(otherTenant, domain.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage("DKIM key was not found")
                .satisfies(exception -> {
                    ApiException api = (ApiException) exception;
                    assertThat(api.getCode()).isEqualTo("DKIM_KEY_NOT_FOUND");
                    assertThat(exception.getMessage()).doesNotContain("pkcs8");
                    assertThat(exception.getMessage()).doesNotContain(key.getEncryptedPrivateKey());
                    assertThat(exception.getMessage()).doesNotContain(key.getPublicKey());
                });
    }

    @Test
    void signingUsesPersistedKeyAndVerifiesWithPublicKey() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity persisted = service.ensureActiveKey(domain, "texto");
        DkimSigningMaterial material = service.requireSigningMaterial(domain);

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("from", "noreply@acme.example");
        headers.put("to", "person@example.com");
        headers.put("subject", "Hello");
        String body = "hello world\n";
        String header = DkimSigner.sign(domain.getDomain(), material.selector(), material.privateKey(), headers, body);
        String rfc822 = header + "\r\nFrom: noreply@acme.example\r\nTo: person@example.com\r\nSubject: Hello\r\n\r\n"
                + body.replace("\n", "\r\n");

        assertThat(material.selector()).isEqualTo(persisted.getSelector());
        assertThat(DkimSigner.verify(DkimKeyMaterial.publicKeyFromPkcs1(persisted.getPublicKey()), rfc822)).isTrue();
        assertThat(material.toString()).doesNotContain("pkcs8");
        assertThat(persisted.toString()).doesNotContain(persisted.getEncryptedPrivateKey());
    }

    @Test
    void decryptingStoredCiphertextIsStableAcrossCalls() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity persisted = service.ensureActiveKey(domain, "texto");
        byte[] first = protector.decrypt(persisted.getEncryptedPrivateKey());
        byte[] second = protector.decrypt(persisted.getEncryptedPrivateKey());
        assertThat(second).isEqualTo(first);
        assertThat(service.requireSigningMaterial(domain).publicKeyPkcs1()).isEqualTo(persisted.getPublicKey());
    }

    @Test
    void changingActiveKeyChangesSelectorUsedForSigning() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity original = service.ensureActiveKey(domain, "texto");
        original.retire();
        DkimKeyEntity rotated = service.ensureActiveKey(domain, "texto2");
        DkimSigningMaterial material = service.requireSigningMaterial(domain);
        assertThat(material.selector()).isEqualTo("texto2");
        assertThat(material.selector()).isNotEqualTo(original.getSelector());
        assertThat(rotated.getPublicKey()).isNotEqualTo(original.getPublicKey());
    }

    @Test
    void decryptFailureDoesNotExposeKeyMaterial() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyEntity entity = service.ensureActiveKey(domain, "texto");
        DkimKeyEntity tampered = DkimKeyEntity.createActive(
                domain.getId(),
                "texto",
                entity.getPublicKey(),
                DkimKeyProtector.withRawKey(Base64.getDecoder().decode("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="))
                        .encrypt("not-the-key".getBytes(StandardCharsets.UTF_8)),
                2048
        );
        stored.set(tampered);

        assertThatThrownBy(() -> service.requireSigningMaterial(domain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DKIM signing key is unavailable")
                .satisfies(exception -> {
                    assertThat(exception.getMessage()).doesNotContain("pkcs8");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void migratesLegacyPkcs8FromVerificationRecord() {
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "acme.example");
        DkimKeyMaterial.Generated generated = DkimKeyMaterial.generate();
        DomainVerificationRecordEntity record = DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_DKIM,
                DkimDnsRecord.ownerName("texto", domain.getDomain()),
                DkimDnsRecord.generate(generated.publicKeyPkcs1()),
                "texto",
                generated.publicKeyPkcs1(),
                generated.privateKeyPkcs8()
        );
        when(verificationRecordRepository.findByDomainIdOrderByTypeAsc(domain.getId())).thenReturn(List.of(record));
        when(verificationRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DkimKeyEntity migrated = service.ensureActiveKey(domain, "texto");
        assertThat(migrated.getPublicKey()).isEqualTo(generated.publicKeyPkcs1());
        assertThat(migrated.getEncryptedPrivateKey()).startsWith("dk1:");
        assertThat(record.getPrivateKeyRef()).isEqualTo(DkimKeyService.reference(migrated.getId()));
        verify(verificationRecordRepository).save(record);
    }
}
