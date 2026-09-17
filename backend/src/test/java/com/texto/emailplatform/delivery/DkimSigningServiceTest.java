package com.texto.emailplatform.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.domain.DkimKeyMaterial;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.domain.DomainVerificationService;
import com.texto.emailplatform.domain.dkim.DkimSigningMaterial;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DkimSigningServiceTest {

    private final DomainRepository domainRepository = Mockito.mock(DomainRepository.class);
    private final DomainService domainService = Mockito.mock(DomainService.class);
    private final DomainVerificationService domainVerificationService = Mockito.mock(DomainVerificationService.class);
    private DkimSigningService signingService;

    @BeforeEach
    void setUp() {
        signingService = new DkimSigningService(domainRepository, domainService, domainVerificationService);
    }

    @Test
    void signsWithPersistedMaterialAndDoesNotFallBackUnsigned() {
        UUID tenantId = UUID.randomUUID();
        DomainEntity domain = DomainEntity.create(tenantId, "acme.example");
        DkimKeyMaterial.Generated generated = DkimKeyMaterial.generate();
        DkimSigningMaterial material = new DkimSigningMaterial(
                "texto",
                generated.publicKeyPkcs1(),
                DkimKeyMaterial.loadPrivateKey(generated.privateKeyPkcs8())
        );
        when(domainRepository.findByTenantIdAndDomain(tenantId, "acme.example")).thenReturn(Optional.of(domain));
        when(domainVerificationService.requireSigningMaterial(domain)).thenReturn(material);

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("from", "noreply@acme.example");
        headers.put("subject", "Hi");
        String header = signingService.sign(tenantId, "noreply@acme.example", headers, "body\n");

        assertThat(header).contains("DKIM-Signature:");
        assertThat(header).contains("s=texto");
        assertThat(header).contains("d=acme.example");
        String rfc822 = header + "\r\nFrom: noreply@acme.example\r\nSubject: Hi\r\n\r\nbody\r\n";
        assertThat(DkimSigner.verify(DkimKeyMaterial.publicKeyFromPkcs1(generated.publicKeyPkcs1()), rfc822)).isTrue();
    }

    @Test
    void otherTenantCannotSignUsingForeignDomain() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        DomainEntity domainA = DomainEntity.create(tenantA, "acme.example");
        when(domainRepository.findByTenantIdAndDomain(tenantB, "acme.example")).thenReturn(Optional.empty());
        when(domainService.ensurePlatformTestDomain(tenantB)).thenReturn(null);

        assertThatThrownBy(() -> signingService.sign(
                tenantB,
                "noreply@acme.example",
                new LinkedHashMap<>(),
                "body"
        ))
                .isInstanceOf(DkimSigningException.class)
                .hasMessage("DKIM signing domain is unavailable")
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("pkcs8"));
        verify(domainVerificationService, never()).requireSigningMaterial(domainA);
    }

    @Test
    void signingFailureDoesNotLeakPrivateKeyInException() {
        UUID tenantId = UUID.randomUUID();
        DomainEntity domain = DomainEntity.create(tenantId, "acme.example");
        when(domainRepository.findByTenantIdAndDomain(tenantId, "acme.example")).thenReturn(Optional.of(domain));
        when(domainVerificationService.requireSigningMaterial(domain))
                .thenThrow(new IllegalStateException("pkcs8:MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSj"));

        assertThatThrownBy(() -> signingService.sign(tenantId, "noreply@acme.example", new LinkedHashMap<>(), "body"))
                .isInstanceOf(DkimSigningException.class)
                .hasMessage("Unable to DKIM-sign the message")
                .satisfies(exception -> {
                    assertThat(exception.getMessage()).doesNotContain("pkcs8");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void missingDomainFailsClosed() {
        UUID tenantId = UUID.randomUUID();
        when(domainRepository.findByTenantIdAndDomain(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> signingService.sign(tenantId, "nobody@missing.example", new LinkedHashMap<>(), "body"))
                .isInstanceOf(DkimSigningException.class);
        verify(domainVerificationService, never()).requireSigningMaterial(any());
    }
}
