package com.texto.emailplatform.ops;

import com.texto.emailplatform.auth.domain.ApiKeyEntity;
import com.texto.emailplatform.auth.domain.ApiKeyRepository;
import com.texto.emailplatform.auth.domain.UserEntity;
import com.texto.emailplatform.auth.domain.UserRepository;
import com.texto.emailplatform.bounce.domain.BounceEventEntity;
import com.texto.emailplatform.bounce.domain.BounceEventRepository;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.complaint.domain.ComplaintEventEntity;
import com.texto.emailplatform.complaint.domain.ComplaintEventRepository;
import com.texto.emailplatform.delivery.mta.MtaHealthIndicator;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;
import com.texto.emailplatform.email.MessageStateMachine;
import com.texto.emailplatform.email.domain.DeliveryAttemptEntity;
import com.texto.emailplatform.email.domain.DeliveryAttemptRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.entitlement.api.EntitlementsResponse;
import com.texto.emailplatform.observability.domain.HttpRequestAuditEntity;
import com.texto.emailplatform.observability.domain.HttpRequestAuditRepository;
import com.texto.emailplatform.ops.api.OpsApiKeyResponse;
import com.texto.emailplatform.ops.api.OpsAuditEventResponse;
import com.texto.emailplatform.ops.api.OpsBounceEventResponse;
import com.texto.emailplatform.ops.api.OpsComplaintEventResponse;
import com.texto.emailplatform.ops.api.OpsDashboardResponse;
import com.texto.emailplatform.ops.api.OpsDashboardResponse.OpsActivityItem;
import com.texto.emailplatform.ops.api.OpsDeliveryAttemptResponse;
import com.texto.emailplatform.ops.api.OpsDomainResponse;
import com.texto.emailplatform.ops.api.OpsDomainResponse.DnsRecordStatus;
import com.texto.emailplatform.ops.api.OpsEmailDetailResponse;
import com.texto.emailplatform.ops.api.OpsEmailSummaryResponse;
import com.texto.emailplatform.ops.api.OpsHealthResponse;
import com.texto.emailplatform.ops.api.OpsHttpRequestAuditResponse;
import com.texto.emailplatform.ops.api.OpsOutboxEventResponse;
import com.texto.emailplatform.ops.api.OpsPageResponse;
import com.texto.emailplatform.ops.api.OpsQueueStatusResponse;
import com.texto.emailplatform.ops.api.OpsQueueStatusResponse.OpsQueueDepth;
import com.texto.emailplatform.ops.api.OpsSuppressionResponse;
import com.texto.emailplatform.ops.api.OpsTenantResponse;
import com.texto.emailplatform.ops.api.OpsTenantUsageResponse;
import com.texto.emailplatform.ops.api.OpsTimelineEvent;
import com.texto.emailplatform.ops.api.OpsUserResponse;
import com.texto.emailplatform.ops.api.OpsWebhookEventResponse;
import com.texto.emailplatform.ops.domain.PlatformAuditEventEntity;
import com.texto.emailplatform.ops.domain.PlatformAuditEventRepository;
import com.texto.emailplatform.outbox.OutboxEventEntity;
import com.texto.emailplatform.outbox.OutboxEventRepository;
import com.texto.emailplatform.plan.domain.PlanEntity;
import com.texto.emailplatform.plan.domain.PlanRepository;
import com.texto.emailplatform.queue.EmailDeliveryQueues;
import com.texto.emailplatform.queue.EmailQueues;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.suppression.domain.SuppressionEntity;
import com.texto.emailplatform.suppression.domain.SuppressionRepository;
import com.texto.emailplatform.tenant.domain.TenantEntity;
import com.texto.emailplatform.tenant.domain.TenantRepository;
import com.texto.emailplatform.usage.UsageService;
import com.texto.emailplatform.usage.api.UsageResponse;
import com.texto.emailplatform.webhook.WebhookQueues;
import com.texto.emailplatform.webhook.domain.WebhookEventEntity;
import com.texto.emailplatform.webhook.domain.WebhookEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsCatalogService {

    private final EmailMessageRepository emailMessageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final BounceEventRepository bounceEventRepository;
    private final ComplaintEventRepository complaintEventRepository;
    private final SuppressionRepository suppressionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DomainRepository domainRepository;
    private final DomainVerificationRecordRepository domainVerificationRecordRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PlatformAuditEventRepository auditEventRepository;
    private final HttpRequestAuditRepository httpRequestAuditRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final EmailPlatformProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final MtaHealthIndicator mtaHealthIndicator;

    public OpsCatalogService(
            EmailMessageRepository emailMessageRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            BounceEventRepository bounceEventRepository,
            ComplaintEventRepository complaintEventRepository,
            SuppressionRepository suppressionRepository,
            OutboxEventRepository outboxEventRepository,
            DomainRepository domainRepository,
            DomainVerificationRecordRepository domainVerificationRecordRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            ApiKeyRepository apiKeyRepository,
            WebhookEventRepository webhookEventRepository,
            PlatformAuditEventRepository auditEventRepository,
            HttpRequestAuditRepository httpRequestAuditRepository,
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            EntitlementService entitlementService,
            UsageService usageService,
            EmailPlatformProperties properties,
            RabbitTemplate rabbitTemplate,
            ObjectProvider<RedisConnectionFactory> redis,
            ObjectProvider<MtaHealthIndicator> mtaHealth
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.bounceEventRepository = bounceEventRepository;
        this.complaintEventRepository = complaintEventRepository;
        this.suppressionRepository = suppressionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.domainRepository = domainRepository;
        this.domainVerificationRecordRepository = domainVerificationRecordRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.auditEventRepository = auditEventRepository;
        this.httpRequestAuditRepository = httpRequestAuditRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.redisConnectionFactory = redis.getIfAvailable();
        this.mtaHealthIndicator = mtaHealth.getIfAvailable();
    }

    @Transactional(readOnly = true)
    public OpsDashboardResponse dashboard(UUID tenantId) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : emailMessageRepository.countGroupedByStatus(tenantId)) {
            byStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }
        long queued = byStatus.getOrDefault(MessageStateMachine.QUEUED, 0L);
        long processing = byStatus.getOrDefault(MessageStateMachine.PROCESSING, 0L);
        long sending = byStatus.getOrDefault(MessageStateMachine.SENDING, 0L);
        long delivered = byStatus.getOrDefault(MessageStateMachine.DELIVERED, 0L);
        long deferred = byStatus.getOrDefault(MessageStateMachine.DEFERRED, 0L);
        long failed = byStatus.getOrDefault(MessageStateMachine.FAILED, 0L);
        long bounced = byStatus.getOrDefault(MessageStateMachine.BOUNCED, 0L);
        long suppressed = byStatus.getOrDefault(MessageStateMachine.SUPPRESSED, 0L);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long complaints = tenantId == null
                ? complaintEventRepository.count()
                : complaintEventRepository.countByTenantId(tenantId);
        double deliveryRate = rate(delivered, total);
        double bounceRate = rate(bounced, total);
        double complaintRate = rate(complaints, total);

        List<OpsActivityItem> activity = new ArrayList<>();
        List<EmailMessageEntity> recentMessages = tenantId == null
                ? emailMessageRepository.findTop20ByOrderByCreatedAtDesc()
                : emailMessageRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantId);
        for (EmailMessageEntity message : recentMessages) {
            activity.add(new OpsActivityItem(
                    message.getCreatedAt(),
                    "EMAIL",
                    message.getTenantId(),
                    message.getId(),
                    message.getStatus(),
                    message.getStatus()
            ));
        }
        List<BounceEventEntity> recentBounces = tenantId == null
                ? bounceEventRepository.findTop20ByOrderByReceivedAtDesc()
                : bounceEventRepository.findTop20ByTenantIdOrderByReceivedAtDesc(tenantId);
        for (BounceEventEntity bounce : recentBounces) {
            activity.add(new OpsActivityItem(
                    bounce.getReceivedAt(),
                    "BOUNCE",
                    bounce.getTenantId(),
                    bounce.getEmailMessageId(),
                    bounce.getBounceClass(),
                    bounce.getCorrelationStatus()
            ));
        }
        List<ComplaintEventEntity> recentComplaints = tenantId == null
                ? complaintEventRepository.findTop20ByOrderByReceivedAtDesc()
                : complaintEventRepository.findTop20ByTenantIdOrderByReceivedAtDesc(tenantId);
        for (ComplaintEventEntity complaint : recentComplaints) {
            activity.add(new OpsActivityItem(
                    complaint.getReceivedAt(),
                    "COMPLAINT",
                    complaint.getTenantId(),
                    complaint.getEmailMessageId(),
                    complaint.getComplaintType(),
                    complaint.getProcessingStatus()
            ));
        }
        activity.sort(Comparator.comparing(OpsActivityItem::at, Comparator.nullsLast(Comparator.reverseOrder())));
        if (activity.size() > 20) {
            activity = new ArrayList<>(activity.subList(0, 20));
        }
        return new OpsDashboardResponse(
                tenantId,
                total,
                queued,
                processing,
                sending,
                delivered,
                deferred,
                failed,
                bounced,
                suppressed,
                complaints,
                deliveryRate,
                bounceRate,
                complaintRate,
                activity
        );
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsEmailSummaryResponse> emails(
            UUID tenantId,
            String status,
            String sender,
            String recipient,
            String providerMessageId,
            UUID messageId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<EmailMessageEntity> spec = OpsSpecs.<EmailMessageEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.statusEquals(status))
                .and(OpsSpecs.containsIgnoreCase("fromAddress", sender))
                .and(OpsSpecs.containsIgnoreCase("recipient", recipient))
                .and(OpsSpecs.equalsIgnoreCase("providerMessageId", providerMessageId))
                .and(OpsSpecs.createdBetween(from, to));
        if (messageId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("id"), messageId));
        }
        Page<EmailMessageEntity> result = emailMessageRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toEmailSummary));
    }

    @Transactional(readOnly = true)
    public OpsEmailDetailResponse emailDetail(UUID messageId) {
        EmailMessageEntity message = emailMessageRepository.findById(messageId)
                .orElseThrow(() -> notFound("EMAIL_NOT_FOUND", "Email message was not found"));
        List<DeliveryAttemptEntity> attempts = deliveryAttemptRepository.findByMessageIdOrderByAttemptNumberAsc(messageId);
        List<BounceEventEntity> bounces = bounceEventRepository.findByEmailMessageIdOrderByReceivedAtAsc(messageId);
        List<ComplaintEventEntity> complaints = complaintEventRepository.findByEmailMessageIdOrderByReceivedAtAsc(messageId);
        List<SuppressionEntity> suppressions = suppressionRepository.findByMessageIdOrderByCreatedAtAsc(messageId);

        Map<String, Instant> timestamps = new LinkedHashMap<>();
        timestamps.put("createdAt", message.getCreatedAt());
        timestamps.put("queuedAt", message.getQueuedAt());
        timestamps.put("processingAt", message.getProcessingAt());
        timestamps.put("sendingAt", message.getSendingAt());
        timestamps.put("deliveredAt", message.getDeliveredAt());
        timestamps.put("failedAt", message.getFailedAt());
        timestamps.put("updatedAt", message.getUpdatedAt());

        List<OpsTimelineEvent> timeline = new ArrayList<>();
        addTimeline(timeline, message.getQueuedAt(), "STATUS", MessageStateMachine.QUEUED, "queued");
        addTimeline(timeline, message.getProcessingAt(), "STATUS", MessageStateMachine.PROCESSING, "processing");
        addTimeline(timeline, message.getSendingAt(), "STATUS", MessageStateMachine.SENDING, "sending");
        addTimeline(timeline, message.getDeliveredAt(), "STATUS", MessageStateMachine.DELIVERED, "delivered");
        addTimeline(timeline, message.getFailedAt(), "STATUS", message.getStatus(), message.getStatusReason());
        for (DeliveryAttemptEntity attempt : attempts) {
            addTimeline(
                    timeline,
                    attempt.getCompletedAt() == null ? attempt.getStartedAt() : attempt.getCompletedAt(),
                    "ATTEMPT",
                    attempt.getStatus(),
                    "attempt " + attempt.getAttemptNumber()
            );
        }
        for (BounceEventEntity bounce : bounces) {
            addTimeline(timeline, bounce.getReceivedAt(), "BOUNCE", bounce.getBounceClass(), bounce.getCorrelationStatus());
        }
        for (ComplaintEventEntity complaint : complaints) {
            addTimeline(timeline, complaint.getReceivedAt(), "COMPLAINT", complaint.getComplaintType(), complaint.getProcessingStatus());
        }
        for (SuppressionEntity suppression : suppressions) {
            addTimeline(timeline, suppression.getCreatedAt(), "SUPPRESSION", suppression.getType(), suppression.getReason());
        }
        timeline.sort(Comparator.comparing(OpsTimelineEvent::at, Comparator.nullsLast(Comparator.naturalOrder())));

        return new OpsEmailDetailResponse(
                toEmailSummary(message),
                timestamps,
                timeline,
                attempts.stream().map(this::toAttempt).toList(),
                bounces.stream().map(this::toBounce).toList(),
                complaints.stream().map(this::toComplaint).toList(),
                suppressions.stream().map(this::toSuppression).toList()
        );
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsDeliveryAttemptResponse> deliveryAttempts(
            UUID tenantId,
            UUID messageId,
            String status,
            String errorCategory,
            Integer attemptNumber,
            int page,
            int size
    ) {
        Specification<DeliveryAttemptEntity> spec = OpsSpecs.<DeliveryAttemptEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.statusEquals(status))
                .and((root, query, cb) -> OpsSpecs.blank(errorCategory)
                        ? cb.conjunction()
                        : cb.equal(root.get("errorCategory"), errorCategory.trim()))
                .and((root, query, cb) -> messageId == null ? cb.conjunction() : cb.equal(root.get("messageId"), messageId))
                .and((root, query, cb) -> attemptNumber == null
                        ? cb.conjunction()
                        : cb.equal(root.get("attemptNumber"), attemptNumber));
        Page<DeliveryAttemptEntity> result = deliveryAttemptRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toAttempt));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsBounceEventResponse> bounces(
            UUID tenantId,
            String bounceClass,
            String correlationStatus,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<BounceEventEntity> spec = OpsSpecs.<BounceEventEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.receivedBetween(from, to))
                .and((root, query, cb) -> OpsSpecs.blank(bounceClass)
                        ? cb.conjunction()
                        : cb.equal(root.get("bounceClass"), bounceClass.trim()))
                .and((root, query, cb) -> OpsSpecs.blank(correlationStatus)
                        ? cb.conjunction()
                        : cb.equal(root.get("correlationStatus"), correlationStatus.trim()));
        Page<BounceEventEntity> result = bounceEventRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toBounce));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsComplaintEventResponse> complaints(
            UUID tenantId,
            String complaintType,
            String processingStatus,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<ComplaintEventEntity> spec = OpsSpecs.<ComplaintEventEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.receivedBetween(from, to))
                .and((root, query, cb) -> OpsSpecs.blank(complaintType)
                        ? cb.conjunction()
                        : cb.equal(root.get("complaintType"), complaintType.trim()))
                .and((root, query, cb) -> OpsSpecs.blank(processingStatus)
                        ? cb.conjunction()
                        : cb.equal(root.get("processingStatus"), processingStatus.trim()));
        Page<ComplaintEventEntity> result = complaintEventRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toComplaint));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsSuppressionResponse> suppressions(
            UUID tenantId,
            String type,
            String email,
            int page,
            int size
    ) {
        Specification<SuppressionEntity> spec = OpsSpecs.<SuppressionEntity>tenantIdEquals(tenantId)
                .and((root, query, cb) -> OpsSpecs.blank(type) ? cb.conjunction() : cb.equal(root.get("type"), type.trim()))
                .and(OpsSpecs.containsIgnoreCase("normalizedEmail", email));
        Page<SuppressionEntity> result = suppressionRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toSuppression));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsOutboxEventResponse> outbox(UUID tenantId, String status, int page, int size) {
        Specification<OutboxEventEntity> spec = OpsSpecs.<OutboxEventEntity>tenantIdEquals(tenantId)
                .and((root, query, cb) -> {
                    if (OpsSpecs.blank(status)) {
                        return cb.conjunction();
                    }
                    if ("UNPUBLISHED".equalsIgnoreCase(status.trim())) {
                        return cb.isNull(root.get("publishedAt"));
                    }
                    if ("PUBLISHED".equalsIgnoreCase(status.trim())) {
                        return cb.isNotNull(root.get("publishedAt"));
                    }
                    throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "status must be PUBLISHED or UNPUBLISHED");
                });
        Page<OutboxEventEntity> result = outboxEventRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toOutbox));
    }

    public OpsQueueStatusResponse queues() {
        List<String> names = List.of(
                EmailDeliveryQueues.DELIVERY_QUEUE,
                EmailDeliveryQueues.DLQ,
                EmailDeliveryQueues.RETRY_30S_QUEUE,
                EmailDeliveryQueues.RETRY_2M_QUEUE,
                EmailDeliveryQueues.RETRY_10M_QUEUE,
                EmailDeliveryQueues.RETRY_30M_QUEUE,
                EmailDeliveryQueues.RETRY_2H_QUEUE,
                EmailQueues.OUTBOUND_QUEUE,
                EmailQueues.BOUNCE_QUEUE,
                EmailQueues.BOUNCE_DLQ,
                EmailQueues.COMPLAINT_QUEUE,
                EmailQueues.COMPLAINT_DLQ,
                WebhookQueues.DELIVERY_QUEUE,
                WebhookQueues.DLQ
        );
        List<OpsQueueDepth> depths = new ArrayList<>();
        for (String name : names) {
            depths.add(queueDepth(name));
        }
        return new OpsQueueStatusResponse(depths);
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsDomainResponse> domains(UUID tenantId, String status, String domain, int page, int size) {
        Specification<DomainEntity> spec = OpsSpecs.<DomainEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.statusEquals(status))
                .and(OpsSpecs.containsIgnoreCase("domain", domain));
        Page<DomainEntity> result = domainRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        List<UUID> ids = result.getContent().stream().map(DomainEntity::getId).toList();
        Map<UUID, List<DomainVerificationRecordEntity>> records = ids.isEmpty()
                ? Map.of()
                : domainVerificationRecordRepository.findByDomainIdInOrderByTypeAsc(ids).stream()
                        .collect(Collectors.groupingBy(DomainVerificationRecordEntity::getDomainId));
        String mailFrom = properties.getBounce().getDomain();
        List<OpsDomainResponse> items = result.getContent().stream()
                .map(entity -> toDomain(entity, records.getOrDefault(entity.getId(), List.of()), mailFrom))
                .toList();
        return OpsPageResponse.of(items, result);
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsTenantResponse> tenants(String status, String q, int page, int size) {
        Specification<TenantEntity> spec = OpsSpecs.<TenantEntity>statusEquals(status)
                .and((root, query, cb) -> {
                    if (OpsSpecs.blank(q)) {
                        return cb.conjunction();
                    }
                    String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                    return cb.or(
                            cb.like(cb.lower(root.get("name")), like),
                            cb.like(cb.lower(root.get("slug")), like)
                    );
                });
        Page<TenantEntity> result = tenantRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toTenant));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsUserResponse> users(UUID tenantId, String role, String status, int page, int size) {
        Specification<UserEntity> spec = OpsSpecs.<UserEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.statusEquals(status))
                .and((root, query, cb) -> OpsSpecs.blank(role) ? cb.conjunction() : cb.equal(root.get("role"), role.trim()));
        Page<UserEntity> result = userRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toUser));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsApiKeyResponse> apiKeys(UUID tenantId, String status, String environment, int page, int size) {
        Specification<ApiKeyEntity> spec = OpsSpecs.<ApiKeyEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.statusEquals(status))
                .and((root, query, cb) -> OpsSpecs.blank(environment)
                        ? cb.conjunction()
                        : cb.equal(root.get("environment"), environment.trim().toUpperCase(Locale.ROOT)));
        Page<ApiKeyEntity> result = apiKeyRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toApiKey));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsWebhookEventResponse> webhookEvents(
            UUID tenantId,
            String status,
            String eventType,
            int page,
            int size
    ) {
        Specification<WebhookEventEntity> spec = OpsSpecs.<WebhookEventEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.statusEquals(status))
                .and((root, query, cb) -> OpsSpecs.blank(eventType)
                        ? cb.conjunction()
                        : cb.equal(root.get("eventType"), eventType.trim()));
        Page<WebhookEventEntity> result = webhookEventRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toWebhook));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsAuditEventResponse> audit(UUID adminId, Instant from, Instant to, int page, int size) {
        Specification<PlatformAuditEventEntity> spec = OpsSpecs.<PlatformAuditEventEntity>createdBetween(from, to)
                .and((root, query, cb) -> adminId == null ? cb.conjunction() : cb.equal(root.get("adminId"), adminId));
        Page<PlatformAuditEventEntity> result = auditEventRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toAudit));
    }

    @Transactional(readOnly = true)
    public OpsPageResponse<OpsHttpRequestAuditResponse> httpRequestAudit(
            UUID tenantId,
            String requestId,
            Integer statusCode,
            String route,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Specification<HttpRequestAuditEntity> spec = OpsSpecs.<HttpRequestAuditEntity>tenantIdEquals(tenantId)
                .and(OpsSpecs.createdBetween(from, to))
                .and((root, query, cb) -> OpsSpecs.blank(requestId)
                        ? cb.conjunction()
                        : cb.equal(root.get("requestId"), requestId.trim()))
                .and((root, query, cb) -> statusCode == null
                        ? cb.conjunction()
                        : cb.equal(root.get("statusCode"), statusCode))
                .and(OpsSpecs.containsIgnoreCase("route", route));
        Page<HttpRequestAuditEntity> result = httpRequestAuditRepository.findAll(
                spec,
                OpsPageResponse.pageable(page, size, OpsPageResponse.newestFirst())
        );
        return OpsPageResponse.of(result.map(this::toHttpRequestAudit));
    }

    public OpsHealthResponse health() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", pingDatabase());
        components.put("redis", pingRedis());
        components.put("rabbitmq", pingRabbit());
        components.put("mta", pingMta());
        boolean down = components.values().stream().anyMatch(value -> value.startsWith("DOWN"));
        return new OpsHealthResponse(down ? Status.DOWN.getCode() : Status.UP.getCode(), components);
    }

    @Transactional(readOnly = true)
    public OpsTenantUsageResponse usage(UUID tenantId) {
        requireTenant(tenantId);
        SubscriptionEntity subscription = subscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
                .orElseThrow(() -> notFound("SUBSCRIPTION_NOT_FOUND", "No subscription exists for this tenant"));
        PlanEntity plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> notFound("PLAN_NOT_FOUND", "Subscribed plan is missing from the catalog"));
        EntitlementsResponse entitlements = entitlementService.getEntitlements(tenantId);
        UsageResponse usage = usageService.getUsage(tenantId);
        return new OpsTenantUsageResponse(
                tenantId,
                subscription.getId(),
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                entitlements,
                usage
        );
    }

    private String pingDatabase() {
        try {
            tenantRepository.count();
            return Status.UP.getCode();
        } catch (Exception exception) {
            return Status.DOWN.getCode();
        }
    }

    private String pingRedis() {
        if (redisConnectionFactory == null) {
            return "UNKNOWN";
        }
        try (var connection = redisConnectionFactory.getConnection()) {
            return "PONG".equals(connection.ping()) ? Status.UP.getCode() : Status.DOWN.getCode();
        } catch (Exception exception) {
            return Status.DOWN.getCode();
        }
    }

    private String pingRabbit() {
        try {
            Boolean ok = rabbitTemplate.execute(channel -> {
                channel.queueDeclarePassive(EmailDeliveryQueues.DELIVERY_QUEUE);
                return true;
            });
            return Boolean.TRUE.equals(ok) ? Status.UP.getCode() : Status.DOWN.getCode();
        } catch (Exception exception) {
            return Status.DOWN.getCode();
        }
    }

    private String pingMta() {
        if (mtaHealthIndicator == null) {
            return "UNKNOWN";
        }
        Health health = mtaHealthIndicator.health();
        return health.getStatus().getCode();
    }

    private OpsQueueDepth queueDepth(String name) {
        try {
            return rabbitTemplate.execute(channel -> {
                var ok = channel.queueDeclarePassive(name);
                return new OpsQueueDepth(name, ok.getMessageCount(), ok.getConsumerCount(), true);
            });
        } catch (Exception exception) {
            return new OpsQueueDepth(name, 0, 0, false);
        }
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null || tenantRepository.findById(tenantId).isEmpty()) {
            throw notFound("TENANT_NOT_FOUND", "Tenant was not found");
        }
    }

    private OpsEmailSummaryResponse toEmailSummary(EmailMessageEntity entity) {
        return new OpsEmailSummaryResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getStatus(),
                entity.getStatusReason(),
                entity.getFromAddress(),
                entity.getRecipient(),
                entity.getRecipientsTo(),
                entity.getRecipientsCc(),
                entity.getRecipientsBcc(),
                entity.getSuppressedRecipients(),
                entity.getBouncedRecipients(),
                entity.getProviderMessageId(),
                entity.getRfc822MessageId(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getLastError(),
                entity.getQueuedAt(),
                entity.getDeliveredAt(),
                entity.getFailedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OpsDeliveryAttemptResponse toAttempt(DeliveryAttemptEntity entity) {
        return new OpsDeliveryAttemptResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getMessageId(),
                entity.getAttemptNumber(),
                entity.getStatus(),
                entity.getErrorCategory(),
                entity.getErrorMessage(),
                entity.getProviderResponse(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt()
        );
    }

    private OpsBounceEventResponse toBounce(BounceEventEntity entity) {
        return new OpsBounceEventResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmailMessageId(),
                entity.getCorrelationStatus(),
                entity.getBounceClass(),
                entity.getFailureKind(),
                entity.getStatusCode(),
                entity.getDiagnosticCode(),
                entity.getDiagnosticMessage(),
                entity.getOriginalRecipient(),
                entity.getFinalRecipient(),
                entity.getProvider(),
                entity.getProviderEventId(),
                entity.getReceivedAt(),
                entity.getCreatedAt()
        );
    }

    private OpsComplaintEventResponse toComplaint(ComplaintEventEntity entity) {
        return new OpsComplaintEventResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmailMessageId(),
                entity.getCorrelationStatus(),
                entity.getProcessingStatus(),
                entity.getProvider(),
                entity.getProviderEventId(),
                entity.getComplaintType(),
                entity.getRecipient(),
                entity.getOriginalMessageId(),
                entity.getProviderMessageId(),
                entity.getDiagnostic(),
                entity.getOccurredAt(),
                entity.getReceivedAt(),
                entity.getCreatedAt()
        );
    }

    private OpsSuppressionResponse toSuppression(SuppressionEntity entity) {
        return new OpsSuppressionResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getMessageId(),
                entity.getEmail(),
                entity.getType(),
                entity.getSource(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OpsOutboxEventResponse toOutbox(OutboxEventEntity entity) {
        return new OpsOutboxEventResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPublishedAt() == null ? "UNPUBLISHED" : "PUBLISHED",
                entity.getAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getPublishedAt()
        );
    }

    private OpsDomainResponse toDomain(
            DomainEntity entity,
            List<DomainVerificationRecordEntity> records,
            String mailFromDomain
    ) {
        return new OpsDomainResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getDomain(),
                entity.getStatus(),
                entity.getVerificationStatus(),
                mailFromDomain,
                dns(records, DomainVerificationRecordEntity.TYPE_SPF),
                dns(records, DomainVerificationRecordEntity.TYPE_DKIM),
                dns(records, DomainVerificationRecordEntity.TYPE_DMARC),
                dns(records, DomainVerificationRecordEntity.TYPE_OWNERSHIP),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static DnsRecordStatus dns(List<DomainVerificationRecordEntity> records, String type) {
        return records.stream()
                .filter(record -> type.equals(record.getType()))
                .findFirst()
                .map(record -> new DnsRecordStatus(
                        record.getType(),
                        record.getName(),
                        record.getStatus(),
                        record.getSelector(),
                        record.getLastError(),
                        record.getVerifiedAt()
                ))
                .orElse(new DnsRecordStatus(type, null, "MISSING", null, null, null));
    }

    private OpsTenantResponse toTenant(TenantEntity entity) {
        return new OpsTenantResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OpsUserResponse toUser(UserEntity entity) {
        return new OpsUserResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OpsApiKeyResponse toApiKey(ApiKeyEntity entity) {
        return new OpsApiKeyResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getStatus(),
                entity.getEnvironment(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }

    private OpsWebhookEventResponse toWebhook(WebhookEventEntity entity) {
        return new OpsWebhookEventResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getWebhookConfigId(),
                entity.getSourceMessageId(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getLastResponseCode(),
                entity.getLastError(),
                entity.getNextAttemptAt(),
                entity.getLastAttemptAt(),
                entity.getDeliveredAt(),
                entity.getCreatedAt()
        );
    }

    private OpsAuditEventResponse toAudit(PlatformAuditEventEntity entity) {
        return new OpsAuditEventResponse(
                entity.getId(),
                entity.getAdminId(),
                entity.getMethod(),
                entity.getPath(),
                entity.getQueryString(),
                entity.getStatusCode(),
                entity.getDurationMs(),
                entity.getRequestId(),
                entity.getCreatedAt()
        );
    }

    private OpsHttpRequestAuditResponse toHttpRequestAudit(HttpRequestAuditEntity entity) {
        return new OpsHttpRequestAuditResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getTraceId(),
                entity.getOccurredAt(),
                entity.getTenantId(),
                entity.getPrincipalId(),
                entity.getApiKeyId(),
                entity.getHttpMethod(),
                entity.getRoute(),
                entity.getStatusCode(),
                entity.getDurationMs(),
                entity.getRequestSizeBytes(),
                entity.getResponseSizeBytes(),
                entity.getClientIp(),
                entity.getUserAgent(),
                entity.getReferer(),
                entity.getAuthOutcome(),
                entity.getErrorCode(),
                entity.getErrorCategory(),
                entity.getEmailMessageId(),
                entity.getProviderMessageId(),
                entity.getOperationType(),
                entity.getCreatedAt()
        );
    }

    private static void addTimeline(List<OpsTimelineEvent> timeline, Instant at, String type, String status, String detail) {
        if (at == null) {
            return;
        }
        timeline.add(new OpsTimelineEvent(at, type, status, detail));
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return Math.round((numerator * 10_000.0d) / denominator) / 10_000.0d;
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND.value(), code, message);
    }
}
