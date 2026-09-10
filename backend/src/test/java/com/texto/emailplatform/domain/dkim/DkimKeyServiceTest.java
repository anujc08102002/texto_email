package com.texto.emailplatform.domain.dkim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.domain.dkim.domain.DkimKeyEntity;
import com.texto.emailplatform.domain.dkim.domain.DkimKeyRepository;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.security.spec.X509EncodedKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DkimKeyServiceTest {

    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private DkimKeyRepository dkimKeyRepository;

    private DkimKeyService service;
    private DkimKeyProtector protector;

    private UUID tenantA;
    private UUID tenantB;
    private DomainEntity domain;
    private KeyPair keyPair;
    private String publicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        protector = new AesGcmDkimKeyProtector(TEST_KEY, false);
        service = new DkimKeyService(domainRepository, dkimKeyRepository, protector);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        domain = DomainEntity.create(tenantA, "mail.example.test");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private DkimKeyEntity storedKey() {
        String encrypted = protector.encryptPrivateKey(keyPair.getPrivate().getEncoded());
        return DkimKeyEntity.createActive(domain.getId(), "texto", "rsa", 2048, publicKeyBase64, encrypted);
    }

    @Test
    void reconstructsUsablePrivateKeyThatCanSign() throws Exception {
        when(domainRepository.findByIdAndTenantId(domain.getId(), tenantA)).thenReturn(Optional.of(domain));
        when(dkimKeyRepository.findFirstByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), "ACTIVE"))
                .thenReturn(Optional.of(storedKey()));

        DkimSigningKey signingKey = service.getSigningKey(tenantA, domain.getId());

        assertThat(signingKey.selector()).isEqualTo("texto");
        assertThat(signingKey.algorithm()).isEqualTo("rsa");
        assertThat(signingKey.privateKey()).isNotNull();

        // The reconstructed private key must produce a signature verifiable by the published public key.
        byte[] message = "dkim-signing-capability-probe".getBytes();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(signingKey.privateKey());
        signer.update(message);
        byte[] signature = signer.sign();

        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(message);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void tenantCannotRetrieveAnotherTenantsKey() {
        // Tenant B asking for tenant A's domain resolves to nothing (tenant-scoped lookup).
        when(domainRepository.findByIdAndTenantId(domain.getId(), tenantB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSigningKey(tenantB, domain.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("DOMAIN_NOT_FOUND");
    }

    @Test
    void missingActiveKeyFailsClearly() {
        when(domainRepository.findByIdAndTenantId(domain.getId(), tenantA)).thenReturn(Optional.of(domain));
        when(dkimKeyRepository.findFirstByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSigningKey(tenantA, domain.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("DKIM_KEY_NOT_FOUND");
    }

    @Test
    void findActiveSigningKeyReturnsKeyForVerifiedDomain() {
        domain.setStatus("VERIFIED");
        when(domainRepository.findByTenantIdAndDomain(tenantA, "mail.example.test")).thenReturn(Optional.of(domain));
        when(dkimKeyRepository.findFirstByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), "ACTIVE"))
                .thenReturn(Optional.of(storedKey()));

        Optional<DkimSigningKey> key = service.findActiveSigningKey(tenantA, "mail.example.test");

        assertThat(key).isPresent();
        assertThat(key.get().selector()).isEqualTo("texto");
    }

    @Test
    void findActiveSigningKeyEmptyForUnverifiedDomain() {
        // domain is PENDING (default) -> not eligible for signing
        when(domainRepository.findByTenantIdAndDomain(tenantA, "mail.example.test")).thenReturn(Optional.of(domain));

        assertThat(service.findActiveSigningKey(tenantA, "mail.example.test")).isEmpty();
    }

    @Test
    void findActiveSigningKeyEmptyForOtherTenant() {
        // Tenant B has no such domain -> empty (cannot sign with A's key).
        when(domainRepository.findByTenantIdAndDomain(tenantB, "mail.example.test")).thenReturn(Optional.empty());

        assertThat(service.findActiveSigningKey(tenantB, "mail.example.test")).isEmpty();
    }

    @Test
    void findActiveSigningKeyEmptyWhenNoActiveKey() {
        domain.setStatus("VERIFIED");
        when(domainRepository.findByTenantIdAndDomain(tenantA, "mail.example.test")).thenReturn(Optional.of(domain));
        when(dkimKeyRepository.findFirstByDomainIdAndStatusOrderByCreatedAtDesc(domain.getId(), "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThat(service.findActiveSigningKey(tenantA, "mail.example.test")).isEmpty();
    }
}
