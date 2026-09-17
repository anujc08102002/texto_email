package com.texto.emailplatform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.domain.dkim.DkimKeyEntity;
import com.texto.emailplatform.domain.dkim.DkimKeyService;
import com.texto.emailplatform.domain.dns.DkimDnsRecord;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DomainVerificationDkimCustodyTest {

    @Test
    void dnsRecordUsesActivePersistedPublicKeyAndKeyReference() {
        DomainVerificationRecordRepository records = Mockito.mock(DomainVerificationRecordRepository.class);
        DnsLookupService dns = Mockito.mock(DnsLookupService.class);
        DkimKeyService dkimKeyService = Mockito.mock(DkimKeyService.class);
        DomainEntity domain = DomainEntity.create(UUID.randomUUID(), "mail.example.com");
        DkimKeyMaterial.Generated generated = DkimKeyMaterial.generate();
        DkimKeyEntity key = DkimKeyEntity.createActive(
                domain.getId(),
                "texto",
                generated.publicKeyPkcs1(),
                "dk1:not-a-real-ciphertext",
                2048
        );
        when(records.findByDomainIdOrderByTypeAsc(domain.getId())).thenReturn(List.of());
        when(records.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dkimKeyService.ensureActiveKey(domain, "texto")).thenReturn(key);

        DomainVerificationService service = new DomainVerificationService(
                records,
                dns,
                new EmailPlatformProperties(),
                dkimKeyService
        );
        List<DomainVerificationRecordEntity> created = service.ensureRecords(domain);
        DomainVerificationRecordEntity dkim = created.stream()
                .filter(record -> DomainVerificationRecordEntity.TYPE_DKIM.equals(record.getType()))
                .findFirst()
                .orElseThrow();

        assertThat(dkim.getName()).isEqualTo("texto._domainkey.mail.example.com");
        assertThat(dkim.getValue()).isEqualTo(DkimDnsRecord.generate(key.getPublicKey()));
        assertThat(dkim.getPublicKey()).isEqualTo(key.getPublicKey());
        assertThat(dkim.getPrivateKeyRef()).isEqualTo(DkimKeyService.reference(key.getId()));
        assertThat(dkim.getPrivateKeyRef()).doesNotStartWith("pkcs8:");
        assertThat(dkim.getValue()).doesNotContain("pkcs8");
    }
}
