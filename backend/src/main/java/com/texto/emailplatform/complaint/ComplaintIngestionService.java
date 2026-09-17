package com.texto.emailplatform.complaint;

import com.texto.emailplatform.bounce.BounceCorrelationToken;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.complaint.domain.ComplaintEventEntity;
import com.texto.emailplatform.complaint.domain.ComplaintEventRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parse → correlate → persist → apply ComplaintPolicy. Tenant comes from the correlated row.
 */
@Service
public class ComplaintIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintIngestionService.class);

    private final ComplaintCorrelator correlator;
    private final ComplaintEventRepository complaintEventRepository;
    private final ComplaintApplicationService complaintApplicationService;
    private final ComplaintMetrics complaintMetrics;
    private final EmailPlatformProperties properties;

    public ComplaintIngestionService(
            ComplaintCorrelator correlator,
            ComplaintEventRepository complaintEventRepository,
            ComplaintApplicationService complaintApplicationService,
            ComplaintMetrics complaintMetrics,
            EmailPlatformProperties properties
    ) {
        this.correlator = correlator;
        this.complaintEventRepository = complaintEventRepository;
        this.complaintApplicationService = complaintApplicationService;
        this.complaintMetrics = complaintMetrics;
        this.properties = properties;
    }

    @Transactional
    public ComplaintIngestionResult ingest(ComplaintIngestionRequest request) {
        if (request == null) {
            return ComplaintIngestionResult.rejected("request_required");
        }
        return persist(sanitize(request), null);
    }

    @Transactional
    public ComplaintIngestionResult ingestMalformed(byte[] raw, String reason) {
        String hash = ComplaintEventHash.parseFailure(raw);
        Optional<ComplaintEventEntity> existing = complaintEventRepository.findByEventHash(hash);
        if (existing.isPresent()) {
            complaintMetrics.incrementDuplicate();
            return ComplaintIngestionResult.duplicate(existing.get());
        }
        ComplaintEventEntity entity = ComplaintEventEntity.create(
                null,
                null,
                hash,
                ComplaintEventEntity.CORRELATION_PARSE_FAILED,
                ComplaintEventEntity.PROCESSING_PARSE_FAILED,
                "UNKNOWN",
                null,
                ComplaintType.UNKNOWN.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                clip(reason == null ? "malformed" : reason, properties.getComplaint().getMaxDiagnosticLength()),
                Map.of()
        );
        complaintEventRepository.save(entity);
        complaintMetrics.incrementEvent();
        log.info("Complaint parse failed reason={} eventId={} (no suppression)", entity.getDiagnostic(), entity.getId());
        return ComplaintIngestionResult.parseFailed(entity.getDiagnostic(), entity);
    }

    private ComplaintIngestionResult persist(ComplaintIngestionRequest request, String diagnostic) {
        String hash = ComplaintEventHash.identity(request);
        Optional<ComplaintEventEntity> existing = complaintEventRepository.findByEventHash(hash);
        if (existing.isPresent()) {
            complaintMetrics.incrementDuplicate();
            return ComplaintIngestionResult.duplicate(existing.get());
        }

        ComplaintCorrelation correlation = correlator.correlate(request);
        UUID tenantId = correlation.matched() ? correlation.message().getTenantId() : null;
        String storedRecipient = correlation.matched()
                ? ComplaintApplicationService.matchingRecipient(correlation.message(), request.recipient())
                : null;
        ComplaintPolicyDecision decision = ComplaintPolicy.decide(correlation.allowsPolicy(), storedRecipient != null);

        String correlationStatus = correlation.matched()
                ? ComplaintEventEntity.CORRELATION_MATCHED
                : ComplaintEventEntity.CORRELATION_UNMATCHED;
        String processingStatus;
        if (!correlation.matched()) {
            processingStatus = ComplaintEventEntity.PROCESSING_UNCORRELATED;
        } else if (decision.suppresses()) {
            processingStatus = ComplaintEventEntity.PROCESSING_APPLIED;
        } else {
            processingStatus = ComplaintEventEntity.PROCESSING_NO_ACTION;
        }

        String tokenFp = correlator.extractToken(request)
                .map(BounceCorrelationToken::fingerprint)
                .orElse(null);

        ComplaintEventEntity entity = ComplaintEventEntity.create(
                tenantId,
                correlation.matched() ? correlation.message().getId() : null,
                hash,
                correlationStatus,
                processingStatus,
                request.provider(),
                request.providerEventId(),
                ComplaintType.from(request.complaintType()).name(),
                request.recipient(),
                storedRecipient,
                request.messageId(),
                request.providerMessageId(),
                tokenFp,
                request.occurredAt(),
                diagnostic,
                request.metadata()
        );
        complaintEventRepository.save(entity);
        complaintMetrics.incrementEvent();

        if (correlation.matched()) {
            complaintMetrics.incrementCorrelated();
            complaintApplicationService.apply(
                    correlation.message(),
                    storedRecipient,
                    correlation.allowsPolicy()
            );
        } else {
            complaintMetrics.incrementUncorrelated();
        }

        log.info(
                "Complaint ingested eventId={} type={} correlated={} mutationAllowed={} emailMessageId={} tenantId={} tokenFp={}",
                entity.getId(),
                entity.getComplaintType(),
                correlation.matched(),
                correlation.allowsPolicy(),
                entity.getEmailMessageId(),
                tenantId,
                tokenFp == null ? "none" : tokenFp
        );

        if (!correlation.matched()) {
            return ComplaintIngestionResult.uncorrelated(entity);
        }
        return ComplaintIngestionResult.accepted(entity);
    }

    ComplaintIngestionRequest sanitize(ComplaintIngestionRequest request) {
        EmailPlatformProperties.Complaint bounds = properties.getComplaint();
        String provider = clip(blankToUnknown(request.provider()), bounds.getMaxProviderLength());
        String providerEventId = clip(request.providerEventId(), bounds.getMaxProviderEventIdLength());
        String messageId = clip(request.messageId(), bounds.getMaxMessageIdLength());
        String providerMessageId = clip(request.providerMessageId(), bounds.getMaxMessageIdLength());
        String recipient = clip(request.recipient(), bounds.getMaxRecipientLength());
        String correlationToken = clip(request.correlationToken(), bounds.getMaxCorrelationTokenLength());
        String complaintType = ComplaintType.from(request.complaintType()).name();
        return new ComplaintIngestionRequest(
                provider,
                providerEventId,
                messageId,
                providerMessageId,
                recipient,
                correlationToken,
                complaintType,
                request.occurredAt(),
                sanitizeMetadata(request.metadata(), bounds)
        );
    }

    private static Map<String, String> sanitizeMetadata(
            Map<String, String> metadata,
            EmailPlatformProperties.Complaint bounds
    ) {
        LinkedHashMap<String, String> clean = new LinkedHashMap<>();
        if (metadata == null) {
            return clean;
        }
        int remaining = bounds.getMaxMetadataEntries();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            String key = clip(entry.getKey(), 64);
            if (key == null) {
                continue;
            }
            String lowered = key.toLowerCase(Locale.ROOT);
            if (lowered.contains("token") || lowered.contains("secret") || lowered.contains("password")) {
                continue;
            }
            clean.put(key, clip(entry.getValue(), bounds.getMaxMetadataValueLength()));
            remaining--;
        }
        return clean;
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            return trimmed.substring(0, max);
        }
        return trimmed;
    }
}
