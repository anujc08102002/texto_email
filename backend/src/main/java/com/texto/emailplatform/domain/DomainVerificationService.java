package com.texto.emailplatform.domain;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.delivery.mta.ProductionIdentity;
import com.texto.emailplatform.domain.dkim.DkimKeyEntity;
import com.texto.emailplatform.domain.dkim.DkimKeyService;
import com.texto.emailplatform.domain.dkim.DkimSigningMaterial;
import com.texto.emailplatform.domain.dns.DkimDnsRecord;
import com.texto.emailplatform.domain.dns.DmarcRecord;
import com.texto.emailplatform.domain.dns.DmarcSettings;
import com.texto.emailplatform.domain.dns.DnsErrorClassifier;
import com.texto.emailplatform.domain.dns.DnsLookupOutcome;
import com.texto.emailplatform.domain.dns.DnsTxtQueryResult;
import com.texto.emailplatform.domain.dns.DomainOwnershipRecord;
import com.texto.emailplatform.domain.dns.SpfRecord;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Generates and checks DNS records for domain ownership and email authentication.
 *
 * <p>Checks are presence/match of published TXT records. This service does not recursively
 * evaluate SPF (RFC 7208 §4.6.4 10-lookup limit) and does not evaluate DMARC (RFC 7489).
 * Receiving MTAs perform those evaluations. {@code status} on {@link DomainEntity} is
 * authoritative for sender authorization; {@code verificationStatus} is kept in sync.
 */
@Service
public class DomainVerificationService {

    private static final Logger log =
        LoggerFactory.getLogger(DomainVerificationService.class);
    private final DomainVerificationRecordRepository verificationRecordRepository;
    private final DnsLookupService dnsLookupService;
    private final EmailPlatformProperties properties;
    private final DkimKeyService dkimKeyService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DomainVerificationService(
            DomainVerificationRecordRepository verificationRecordRepository,
            DnsLookupService dnsLookupService,
            EmailPlatformProperties properties,
            DkimKeyService dkimKeyService
    ) {
        this.verificationRecordRepository = verificationRecordRepository;
        this.dnsLookupService = dnsLookupService;
        this.properties = properties;
        this.dkimKeyService = dkimKeyService;
    }

    @Transactional
    public List<DomainVerificationRecordEntity> ensureRecords(DomainEntity domain) {
        List<DomainVerificationRecordEntity> existing =
                verificationRecordRepository.findByDomainIdOrderByTypeAsc(domain.getId());
        if (existing.isEmpty()) {
            return verificationRecordRepository.saveAll(createRecords(domain));
        }
        return upgradeRecords(domain, existing);
    }

    /**
     * Reusable verification entry point (manual now; scheduler can call later).
     */
    @Transactional
    public boolean verifyRecords(DomainEntity domain) {
        List<DomainVerificationRecordEntity> records = ensureRecords(domain);
        boolean allVerified = true;
        for (DomainVerificationRecordEntity record : records) {
            boolean verified = verifyOne(domain, record);
            allVerified = allVerified && verified;
        }
        verificationRecordRepository.saveAll(records);
        return allVerified;
    }

    @Transactional
    public void markRecordsVerified(DomainEntity domain) {
        List<DomainVerificationRecordEntity> records = ensureRecords(domain);
        records.forEach(DomainVerificationRecordEntity::markVerified);
        verificationRecordRepository.saveAll(records);
    }

    public DkimSigningMaterial requireSigningMaterial(DomainEntity domain) {
        dkimKeyService.ensureActiveKey(domain, configuredSelector());
        return dkimKeyService.requireSigningMaterial(domain);
    }

    public String dkimSelector(DomainEntity domain) {
        return dkimKeyService.ensureActiveKey(domain, configuredSelector()).getSelector();
    }

    public static String customerDetail(DomainVerificationRecordEntity record) {
        return customerMessage(record.getType(), record.getLastError(), record.getStatus());
    }

    private List<DomainVerificationRecordEntity> createRecords(DomainEntity domain) {
        String token = HexFormat.of().formatHex(randomBytes(16));
        DkimKeyEntity dkimKey = dkimKeyService.ensureActiveKey(domain, configuredSelector());
        String selector = dkimKey.getSelector();
        DmarcSettings dmarc = properties.getDomains().dmarcSettings();

        List<DomainVerificationRecordEntity> records = new ArrayList<>();
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_OWNERSHIP,
                DomainOwnershipRecord.ownerName(domain.getDomain()),
                DomainOwnershipRecord.generate(token),
                null,
                null,
                null
        ));
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_SPF,
                domain.getDomain(),
                expectedSpfRecord(),
                null,
                null,
                null
        ));
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_DKIM,
                DkimDnsRecord.ownerName(selector, domain.getDomain()),
                DkimDnsRecord.generate(dkimKey.getPublicKey()),
                selector,
                dkimKey.getPublicKey(),
                DkimKeyService.reference(dkimKey.getId())
        ));
        records.add(DomainVerificationRecordEntity.create(
                domain.getId(),
                DomainVerificationRecordEntity.TYPE_DMARC,
                "_dmarc." + domain.getDomain(),
                DmarcRecord.generate(dmarc),
                null,
                null,
                null
        ));
        return records;
    }

    private List<DomainVerificationRecordEntity> upgradeRecords(
            DomainEntity domain,
            List<DomainVerificationRecordEntity> existing
    ) {
        boolean changed = false;
        if (existing.stream().noneMatch(record -> DomainVerificationRecordEntity.TYPE_OWNERSHIP.equals(record.getType()))) {
            String token = HexFormat.of().formatHex(randomBytes(16));
            existing = new ArrayList<>(existing);
            existing.add(DomainVerificationRecordEntity.create(
                    domain.getId(),
                    DomainVerificationRecordEntity.TYPE_OWNERSHIP,
                    DomainOwnershipRecord.ownerName(domain.getDomain()),
                    DomainOwnershipRecord.generate(token),
                    null,
                    null,
                    null
            ));
            changed = true;
        }
        for (DomainVerificationRecordEntity record : existing) {
            if (DomainVerificationRecordEntity.TYPE_SPF.equals(record.getType())) {
                String expected = expectedSpfRecord();
                if (!expected.equals(record.getValue()) || containsOwnershipMarker(record.getValue())) {
                    record.replaceValue(expected);
                    changed = true;
                }
            } else if (DomainVerificationRecordEntity.TYPE_DKIM.equals(record.getType())) {
                DkimKeyEntity dkimKey = dkimKeyService.ensureActiveKey(domain, configuredSelector());
                String expectedDns = DkimDnsRecord.generate(dkimKey.getPublicKey());
                boolean stalePublic = record.getPublicKey() == null
                        || !dkimKey.getPublicKey().replaceAll("\\s+", "").equals(record.getPublicKey().replaceAll("\\s+", ""));
                boolean staleRef = DkimKeyMaterial.isStoredPrivateKey(record.getPrivateKeyRef())
                        || record.getPrivateKeyRef() == null
                        || !DkimKeyService.isKeyReference(record.getPrivateKeyRef());
                boolean staleSelector = record.getSelector() == null || !dkimKey.getSelector().equals(record.getSelector());
                if (stalePublic || staleRef || staleSelector || containsOwnershipMarker(record.getValue())
                        || !expectedDns.equals(record.getValue())) {
                    record.syncDkimDns(
                            expectedDns,
                            dkimKey.getPublicKey(),
                            dkimKey.getSelector(),
                            DkimKeyService.reference(dkimKey.getId())
                    );
                    changed = true;
                }
            } else if (DomainVerificationRecordEntity.TYPE_DMARC.equals(record.getType())) {
                String expected = DmarcRecord.generate(properties.getDomains().dmarcSettings());
                if (containsOwnershipMarker(record.getValue()) || record.getValue() == null || record.getValue().isBlank()) {
                    record.replaceValue(expected);
                    changed = true;
                }
            }
        }
        if (changed) {
            verificationRecordRepository.saveAll(existing);
            return verificationRecordRepository.findByDomainIdOrderByTypeAsc(domain.getId());
        }
        return existing;
    }

    private boolean verifyOne(DomainEntity domain, DomainVerificationRecordEntity record) {
        DnsTxtQueryResult lookup = dnsLookupService.lookupTxt(record.getName());
        if (lookup.outcome() != DnsLookupOutcome.OK) {
            applyLookupFailure(record, lookup.outcome());
            return false;
        }
        return switch (record.getType()) {
            case DomainVerificationRecordEntity.TYPE_OWNERSHIP -> verifyOwnership(record, lookup);
            case DomainVerificationRecordEntity.TYPE_SPF -> verifySpf(record, lookup);
            case DomainVerificationRecordEntity.TYPE_DKIM -> verifyDkim(domain, record, lookup);
            case DomainVerificationRecordEntity.TYPE_DMARC -> verifyDmarc(record, lookup);
            default -> {
                record.markFailed("UNSUPPORTED_RECORD");
                yield false;
            }
        };
    }

    private boolean verifyOwnership(DomainVerificationRecordEntity record, DnsTxtQueryResult lookup) {
        String token = DomainOwnershipRecord.extractToken(record.getValue());
        DomainOwnershipRecord.MatchResult result = DomainOwnershipRecord.match(lookup.txtRecords(), token);
        if (result.isMatched()) {
            record.markVerified();
            return true;
        }
        if (result.status() == DomainOwnershipRecord.Status.MISSING) {
            record.markMissing("OWNERSHIP_MISSING");
        } else {
            record.markFailed("OWNERSHIP_MISMATCH");
        }
        return false;
    }

    private boolean verifySpf(DomainVerificationRecordEntity record, DnsTxtQueryResult lookup) {
        SpfRecord.MatchResult result = SpfRecord.match(
                lookup.txtRecords(),
                properties.getDomains().getSpfInclude(),
                properties.getDomains().getSpfAllQualifier(),
                expectedSpfIp4()
        );
        if (result.isMatched()) {
            record.markVerified();
            return true;
        }
        switch (result.status()) {
            case MISSING -> record.markMissing("SPF_MISSING");
            case MULTIPLE -> record.markFailed("SPF_MULTIPLE");
            case MALFORMED -> record.markFailed("SPF_MALFORMED");
            default -> record.markFailed("SPF_MISMATCH");
        }
        return false;
    }

    private static String fingerprint(String value) {
        if (value == null) {
            return "null";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.replaceAll("\\s+", "")
                            .getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "fingerprint-error";
        }
    }
     private static String normalizeKey(String key) {
        return key == null ? "" : key.replaceAll("\\s+", "");
    }

    private boolean verifyDkim(
            DomainEntity domain,
            DomainVerificationRecordEntity record,
           
            DnsTxtQueryResult lookup) {
    
        DkimKeyEntity active = dkimKeyService.ensureActiveKey(domain, configuredSelector());
    
        String activePublicKey = normalizeKey(active.getPublicKey());
        String recordPublicKey = normalizeKey(record.getPublicKey());
    
        // The verification record must correspond to the currently active DKIM key.
        if (activePublicKey.isEmpty() || !activePublicKey.equals(recordPublicKey)) {
            record.markFailed("DKIM_ACTIVE_KEY_MISMATCH");
    
            log.warn(
                    "DKIM active-key mismatch: domain={}, selector={}, activeFingerprint={}, recordFingerprint={}",
                    domain.getDomain(),
                    active.getSelector(),
                    fingerprint(active.getPublicKey()),
                    fingerprint(record.getPublicKey())
            );
    
            return false;
        }
    
        DkimDnsRecord.MatchResult result =
                DkimDnsRecord.match(lookup.txtRecords(), active.getPublicKey());
    
        if (result.isMatched()) {
            record.markVerified();
    
            log.info(
                    "DKIM DNS verification successful: domain={}, selector={}, fingerprint={}",
                    domain.getDomain(),
                    active.getSelector(),
                    fingerprint(active.getPublicKey())
            );
    
            return true;
        }
    
        switch (result.status()) {
            case MISSING -> {
                record.markMissing("DKIM_MISSING");
    
                log.warn(
                        "DKIM DNS record missing: domain={}, selector={}",
                        domain.getDomain(),
                        active.getSelector()
                );
            }
    
            case MULTIPLE -> {
                record.markFailed("DKIM_MULTIPLE");
    
                log.warn(
                        "Multiple DKIM DNS records found: domain={}, selector={}",
                        domain.getDomain(),
                        active.getSelector()
                );
            }
    
            case REVOKED -> {
                record.markFailed("DKIM_REVOKED");
    
                log.warn(
                        "DKIM record is revoked: domain={}, selector={}",
                        domain.getDomain(),
                        active.getSelector()
                );
            }
    
            default -> {
                record.markFailed("DKIM_DNS_MISMATCH");
    
                log.warn(
                        "DKIM DNS public key mismatch: domain={}, selector={}, expectedFingerprint={}",
                        domain.getDomain(),
                        active.getSelector(),
                        fingerprint(active.getPublicKey())
                );
            }
        }
    
        return false;
    }

    private boolean verifyDmarc(DomainVerificationRecordEntity record, DnsTxtQueryResult lookup) {
        DmarcRecord.MatchResult result = DmarcRecord.match(lookup.txtRecords(), properties.getDomains().dmarcSettings());
        if (result.isMatched()) {
            record.markVerified();
            return true;
        }
        switch (result.status()) {
            case MISSING -> record.markMissing("DMARC_MISSING");
            case MULTIPLE -> record.markFailed("DMARC_MULTIPLE");
            case MALFORMED -> record.markFailed("DMARC_MALFORMED");
            default -> record.markFailed("DMARC_MISMATCH");
        }
        return false;
    }

    private static void applyLookupFailure(DomainVerificationRecordEntity record, DnsLookupOutcome outcome) {
        String code = switch (outcome) {
            case NXDOMAIN -> "DNS_NXDOMAIN";
            case TIMEOUT -> "DNS_TIMEOUT";
            case TEMPORARY_FAILURE -> "DNS_TEMPORARY";
            case MALFORMED -> "DNS_MALFORMED";
            default -> "DNS_MISSING";
        };
        if (outcome == DnsLookupOutcome.MISSING || outcome == DnsLookupOutcome.NXDOMAIN) {
            record.markMissing(code);
        } else {
            record.markFailed(code);
        }
    }

    static String customerMessage(String type, String lastError, String status) {
        if (lastError == null || lastError.isBlank()) {
            return null;
        }
        return switch (lastError) {
            case "OWNERSHIP_MISSING" -> "Ownership TXT was not found.";
            case "OWNERSHIP_MISMATCH" -> "Ownership token did not match.";
            case "SPF_MISSING" -> "SPF TXT was not found.";
            case "SPF_MULTIPLE" -> "Multiple SPF records were found. Publish exactly one v=spf1 record.";
            case "SPF_MALFORMED" -> "The SPF record is not valid.";
            case "SPF_MISMATCH" -> "The SPF record does not match the required include.";
            case "DKIM_MISSING" -> "DKIM TXT was not found.";
            case "DKIM_MULTIPLE" -> "Multiple DKIM TXT records were found for this selector.";
            case "DKIM_REVOKED" -> "The DKIM public key has been revoked.";
            case "DKIM_ACTIVE_KEY_MISMATCH" -> "The DKIM verification record does not match the active signing key.";
            case "DKIM_DNS_MISMATCH" -> "The published DKIM public key does not match the active signing key.";
            case "DMARC_MISSING" -> "DMARC TXT was not found.";
            case "DMARC_MULTIPLE" -> "Multiple DMARC records were found.";
            case "DMARC_MALFORMED" -> "The DMARC record is not valid.";
            case "DMARC_MISMATCH" -> "The DMARC policy does not match the required record.";
            case "DNS_NXDOMAIN" -> DnsErrorClassifier.customerMessage(DnsLookupOutcome.NXDOMAIN);
            case "DNS_TIMEOUT" -> DnsErrorClassifier.customerMessage(DnsLookupOutcome.TIMEOUT);
            case "DNS_TEMPORARY" -> DnsErrorClassifier.customerMessage(DnsLookupOutcome.TEMPORARY_FAILURE);
            case "DNS_MALFORMED" -> DnsErrorClassifier.customerMessage(DnsLookupOutcome.MALFORMED);
            case "DNS_MISSING" -> DnsErrorClassifier.customerMessage(DnsLookupOutcome.MISSING);
            default -> DomainVerificationRecordEntity.STATUS_MISSING.equals(status)
                    ? "This DNS record was not found."
                    : "This DNS record could not be verified.";
        };
    }

    private DomainVerificationRecordEntity requireDkim(DomainEntity domain) {
        return ensureRecords(domain).stream()
                .filter(record -> DomainVerificationRecordEntity.TYPE_DKIM.equals(record.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DKIM record is missing for " + domain.getDomain()));
    }

    private String expectedSpfRecord() {
        return SpfRecord.generate(
                properties.getDomains().getSpfInclude(),
                properties.getDomains().getSpfAllQualifier(),
                expectedSpfIp4()
        );
    }

    private String expectedSpfIp4() {
        if (!properties.getDomains().isSpfAuthorizeOutboundIp()) {
            return null;
        }
        String ip = properties.getMta().getOutboundIp();
        return ProductionIdentity.isPublicIpv4(ip) ? ip.trim() : null;
    }

    private String configuredSelector() {
        String selector = properties.getDomains().getDkimSelector();
        if (selector == null || selector.isBlank()) {
            return "texto";
        }
        return selector.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsOwnershipMarker(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("texto-verify-") || lower.contains("texto-domain-verification=");
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
