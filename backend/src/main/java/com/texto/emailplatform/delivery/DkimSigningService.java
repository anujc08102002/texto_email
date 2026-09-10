package com.texto.emailplatform.delivery;

import com.texto.emailplatform.domain.DomainNormalizer;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.domain.DomainVerificationService;
import com.texto.emailplatform.domain.PlatformSenderDomains;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DkimSigningService {

    private static final Logger log = LoggerFactory.getLogger(DkimSigningService.class);

    private final DomainRepository domainRepository;
    private final DomainService domainService;
    private final DomainVerificationService domainVerificationService;

    public DkimSigningService(
            DomainRepository domainRepository,
            DomainService domainService,
            DomainVerificationService domainVerificationService
    ) {
        this.domainRepository = domainRepository;
        this.domainService = domainService;
        this.domainVerificationService = domainVerificationService;
    }

    @Transactional
    public Optional<String> sign(
            UUID tenantId,
            String fromAddress,
            LinkedHashMap<String, String> headers,
            String body
    ) {
        DomainEntity domain = resolveDomain(tenantId, fromAddress);
        if (domain == null) {
            return Optional.empty();
        }
        try {
            PrivateKey privateKey = domainVerificationService.requireDkimPrivateKey(domain);
            String selector = domainVerificationService.dkimSelector(domain);
            return Optional.of(DkimSigner.sign(domain.getDomain(), selector, privateKey, headers, body == null ? "" : body));
        } catch (RuntimeException exception) {
            log.warn("DKIM signing skipped for {}: {}", domain.getDomain(), exception.getMessage());
            return Optional.empty();
        }
    }

    private DomainEntity resolveDomain(UUID tenantId, String fromAddress) {
        if (tenantId == null || fromAddress == null || !fromAddress.contains("@")) {
            return null;
        }
        String host = DomainNormalizer.normalize(fromAddress.substring(fromAddress.lastIndexOf('@') + 1));
        DomainEntity entity = domainRepository.findByTenantIdAndDomain(tenantId, host).orElse(null);
        if (entity != null) {
            return entity;
        }
        if (!PlatformSenderDomains.isPlatformTestDomain(host)) {
            return null;
        }
        DomainEntity platform = domainService.ensurePlatformTestDomain(tenantId);
        if (platform != null && host.equals(platform.getDomain())) {
            return platform;
        }
        return null;
    }
}
