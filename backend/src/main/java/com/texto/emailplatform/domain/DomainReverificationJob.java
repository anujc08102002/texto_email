package com.texto.emailplatform.domain;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically detects DNS authentication drift for previously verified domains. */
@Component
public class DomainReverificationJob {

    private final DomainService domainService;

    public DomainReverificationJob(DomainService domainService) {
        this.domainService = domainService;
    }

    @Scheduled(cron = "${email-platform.domains.reverify-cron:0 17 3 * * *}")
    public void reverify() {
        domainService.reverifyVerifiedDomains();
    }
}
