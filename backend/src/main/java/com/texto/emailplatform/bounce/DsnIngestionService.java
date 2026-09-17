package com.texto.emailplatform.bounce;

import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parse → correlate → persist → apply bounce policy. Tenant comes from the correlated row.
 * Acks happen only after this transaction commits.
 */
@Service
public class DsnIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DsnIngestionService.class);

    private final DsnParser parser;
    private final BounceCorrelator correlator;
    private final BounceEventRepository bounceEventRepository;
    private final BounceApplicationService bounceApplicationService;
    private final BounceMetrics bounceMetrics;

    public DsnIngestionService(
            DsnParser parser,
            BounceCorrelator correlator,
            BounceEventRepository bounceEventRepository,
            BounceApplicationService bounceApplicationService,
            BounceMetrics bounceMetrics
    ) {
        this.parser = parser;
        this.correlator = correlator;
        this.bounceEventRepository = bounceEventRepository;
        this.bounceApplicationService = bounceApplicationService;
        this.bounceMetrics = bounceMetrics;
    }

    @Transactional
    public DsnIngestionResult ingest(DsnIngestionRequest request) {
        if (request == null) {
            return DsnIngestionResult.rejected("request_required");
        }
        ParsedDsn parsed;
        try {
            parsed = parser.parse(request.rawRfc822());
        } catch (RuntimeException exception) {
            log.warn("DSN parser threw type={}", exception.getClass().getSimpleName());
            parsed = ParsedDsn.failure("malformed_mime");
        }
        if (!parsed.success()) {
            return persistParseFailure(request, parsed);
        }
        return persistRecipients(request, parsed);
    }

    private DsnIngestionResult persistParseFailure(DsnIngestionRequest request, ParsedDsn parsed) {
        BounceCorrelation correlation = correlator.correlate(parsed, null, request.envelopeRecipient());
        UUID tenantId = correlation.matched() ? correlation.message().getTenantId() : null;
        String hash = BounceEventHash.parseFailure(tenantId, request.rawRfc822());
        Optional<BounceEventEntity> existing = bounceEventRepository.findByEventHash(hash);
        if (existing.isPresent()) {
            bounceMetrics.incrementDuplicate();
            return DsnIngestionResult.duplicate(List.of(existing.get()));
        }
        BounceEventEntity entity = BounceEventEntity.create(
                tenantId,
                correlation.matched() ? correlation.message().getId() : null,
                hash,
                BounceEventEntity.CORRELATION_PARSE_FAILED,
                BounceClass.UNKNOWN,
                BounceFailureKind.UNKNOWN,
                DsnAction.UNKNOWN,
                null,
                null,
                clipReason(parsed.failureReason()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        bounceEventRepository.save(entity);
        bounceMetrics.incrementEvent();
        log.info(
                "DSN parse failed reason={} eventId={} tenantId={} correlated={} (no delivery mutation)",
                parsed.failureReason(),
                entity.getId(),
                tenantId,
                correlation.matched()
        );
        return DsnIngestionResult.parseFailed(parsed.failureReason(), List.of(entity));
    }

    private DsnIngestionResult persistRecipients(DsnIngestionRequest request, ParsedDsn parsed) {
        List<DsnRecipient> recipients = parsed.recipients();
        if (recipients.isEmpty()) {
            recipients = List.of(emptyRecipient());
        }
        List<BounceEventEntity> stored = new ArrayList<>();
        int created = 0;
        int matchedCount = 0;
        for (DsnRecipient recipient : recipients) {
            BounceCorrelation correlation = correlator.correlate(parsed, recipient, request.envelopeRecipient());
            UUID tenantId = correlation.matched() ? correlation.message().getTenantId() : null;
            BounceClassification classification = recipient.classification() == null
                    ? BounceClassifier.classify(recipient.action(), recipient.status(), recipient.diagnosticMessage())
                    : recipient.classification();
            String hash = BounceEventHash.canonical(
                    tenantId,
                    parsed.originalMessageId(),
                    recipient.finalRecipient() != null ? recipient.finalRecipient() : recipient.originalRecipient(),
                    recipient.action(),
                    recipient.status(),
                    recipient.diagnosticCode(),
                    parsed.reportingMta(),
                    parsed.arrivalDate() == null ? null : parsed.arrivalDate().toString()
            );
            Optional<BounceEventEntity> existing = bounceEventRepository.findByEventHash(hash);
            if (existing.isPresent()) {
                stored.add(existing.get());
                bounceMetrics.incrementDuplicate();
                continue;
            }
            BounceEventEntity entity = BounceEventEntity.create(
                    tenantId,
                    correlation.matched() ? correlation.message().getId() : null,
                    hash,
                    correlation.matched() ? BounceEventEntity.CORRELATION_MATCHED : BounceEventEntity.CORRELATION_UNMATCHED,
                    classification.bounceClass(),
                    classification.failureKind(),
                    classification.action(),
                    classification.statusCode(),
                    recipient.diagnosticCode(),
                    recipient.diagnosticMessage(),
                    recipient.originalRecipient(),
                    recipient.finalRecipient(),
                    parsed.originalSender(),
                    parsed.originalMessageId(),
                    parsed.reportingMta(),
                    recipient.remoteMta(),
                    parsed.arrivalDate(),
                    recipient.lastAttemptDate(),
                    recipient.willRetryUntil()
            );
            bounceEventRepository.save(entity);
            stored.add(entity);
            created++;
            bounceMetrics.incrementEvent();
            bounceMetrics.recordClassification(classification.bounceClass());
            if (correlation.matched()) {
                matchedCount++;
                String storedRecipient = BounceRecipientMatcher.matchingStoredRecipient(correlation.message(), recipient);
                bounceApplicationService.apply(
                        correlation.message(),
                        storedRecipient,
                        classification,
                        correlation.allowsStateMutation()
                );
            } else {
                bounceMetrics.incrementUncorrelated();
            }
            log.info(
                    "DSN ingested eventId={} bounceClass={} failureKind={} correlated={} mutationAllowed={} emailMessageId={} tenantId={} tokenFp={}",
                    entity.getId(),
                    entity.getBounceClass(),
                    entity.getFailureKind(),
                    correlation.matched(),
                    correlation.allowsStateMutation(),
                    entity.getEmailMessageId(),
                    tenantId,
                    BounceCorrelationToken.fingerprint(
                            correlator.extractToken(parsed, recipient, request.envelopeRecipient()).orElse(null)
                    )
            );
        }
        if (created == 0) {
            return DsnIngestionResult.duplicate(stored);
        }
        if (matchedCount == 0) {
            return DsnIngestionResult.unmatched(stored);
        }
        return DsnIngestionResult.accepted(stored);
    }

    private static DsnRecipient emptyRecipient() {
        BounceClassification classification = BounceClassifier.classify(null, null, null);
        return new DsnRecipient(null, null, null, null, null, null, null, null, null, classification);
    }

    private static String clipReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 512 ? reason : reason.substring(0, 512);
    }
}
