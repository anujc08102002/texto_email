package com.texto.emailplatform.observability;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.observability.domain.HttpRequestAuditRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HttpRequestAuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestAuditRetentionJob.class);

    private final HttpRequestAuditRepository repository;
    private final EmailPlatformProperties properties;

    public HttpRequestAuditRetentionJob(
            HttpRequestAuditRepository repository,
            EmailPlatformProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${email-platform.observability.http-audit-purge-cron:0 25 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int days = Math.max(1, properties.getObservability().getHttpAuditRetentionDays());
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} HTTP request audit rows older than {} days", deleted, days);
        }
    }
}
