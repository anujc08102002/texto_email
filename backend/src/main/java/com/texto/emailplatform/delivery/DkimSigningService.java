package com.texto.emailplatform.delivery;

import com.texto.emailplatform.domain.DomainNormalizer;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.domain.DomainVerificationService;
import com.texto.emailplatform.domain.PlatformSenderDomains;
import com.texto.emailplatform.domain.dkim.DkimSigningMaterial;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import java.util.LinkedHashMap;
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
    public String sign(
            UUID tenantId,
            String fromAddress,
            LinkedHashMap<String, String> headers,
            String body
    ) {
        DomainEntity domain = resolveDomain(tenantId, fromAddress);
        if (domain == null) {
            throw new DkimSigningException("DKIM signing domain is unavailable");
        }
        try {
            DkimSigningMaterial material = domainVerificationService.requireSigningMaterial(domain);
            return DkimSigner.sign(
                    domain.getDomain(),
                    material.selector(),
                    material.privateKey(),
                    headers,
                    body == null ? "" : body
            );
        } catch (DkimSigningException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("DKIM signing failed domain={} type={}", domain.getDomain(), exception.getClass().getSimpleName());
            throw new DkimSigningException("Unable to DKIM-sign the message");
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
