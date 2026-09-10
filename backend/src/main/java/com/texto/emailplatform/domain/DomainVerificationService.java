package com.texto.emailplatform.domain;

import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainVerificationService {

    private static final String DKIM_SELECTOR = "texto";

    private final DomainVerificationRecordRepository verificationRecordRepository;
    private final DnsLookupService dnsLookupService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DomainVerificationService(
            DomainVerificationRecordRepository verificationRecordRepository,
            DnsLookupService dnsLookupService
    ) {
        this.verificationRecordRepository = verificationRecordRepository;
        this.dnsLookupService = dnsLookupService;
    }

    @Transactional
    public List<DomainVerificationRecordEntity> ensureRecords(DomainEntity domain) {
        List<DomainVerificationRecordEntity> existing =
                verificationRecordRepository.findByDomainIdOrderByTypeAsc(domain.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        String token = HexFormat.of().formatHex(randomBytes(8));
        String verifyMarker = "texto-verify-" + token;

        KeyPair keyPair = generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyRef = "local-ref:" + UUID.randomUUID();

        List<DomainVerificationRecordEntity> records = new ArrayList<>();
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_SPF,
                domain.getDomain(),
                "v=spf1 include:_spf.texto.email ~all " + verifyMarker,
                null,
                null,
                null
        ));
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_DKIM,
                DKIM_SELECTOR + "._domainkey." + domain.getDomain(),
                "v=DKIM1; k=rsa; p=" + publicKey + "; " + verifyMarker,
                DKIM_SELECTOR,
                publicKey,
                privateKeyRef
        ));
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_DMARC,
                "_dmarc." + domain.getDomain(),
                "v=DMARC1; p=none; " + verifyMarker,
                null,
                null,
                null
        ));
        return verificationRecordRepository.saveAll(records);
    }

    @Transactional
    public boolean verifyRecords(DomainEntity domain) {
        List<DomainVerificationRecordEntity> records = ensureRecords(domain);
        boolean allVerified = true;
        for (DomainVerificationRecordEntity record : records) {
            List<String> txt = dnsLookupService.lookupTxt(record.getName());
            boolean matched = txt.stream().anyMatch(value -> valuesMatch(value, record.getValue()))
                    || valuesMatch(String.join("", txt), record.getValue());
            if (matched) {
                record.markVerified();
            } else if (txt.isEmpty()) {
                record.markMissing();
                allVerified = false;
            } else {
                record.markFailed();
                allVerified = false;
            }
        }
        verificationRecordRepository.saveAll(records);
        return allVerified;
    }

    private static boolean valuesMatch(String observed, String expected) {
        if (observed == null || expected == null) {
            return false;
        }
        String left = observed.replace("\"", "").replace(" ", "").trim();
        String right = expected.replace("\"", "").replace(" ", "").trim();
        return left.equals(right) || left.contains(right);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate DKIM key pair", exception);
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
