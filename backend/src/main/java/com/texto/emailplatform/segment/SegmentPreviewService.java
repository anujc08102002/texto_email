package com.texto.emailplatform.segment;

import com.texto.emailplatform.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.contact.api.ContactResponse;
import com.texto.emailplatform.contact.domain.ContactEntity;
import com.texto.emailplatform.segment.api.SegmentPreviewResponse;
import com.texto.emailplatform.segment.domain.SegmentEntity;
import com.texto.emailplatform.segment.domain.SegmentRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SegmentPreviewService {
    private final EntityManager entityManager;
    private final SegmentConditionQueryBuilder queryBuilder;
    private final SegmentRepository segments;
    private final ObjectMapper objectMapper;

    public SegmentPreviewService(
            EntityManager entityManager,
            SegmentConditionQueryBuilder queryBuilder,
            SegmentRepository segments,
            ObjectMapper objectMapper
    ) {
        this.entityManager = entityManager;
        this.queryBuilder = queryBuilder;
        this.segments = segments;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SegmentPreviewResponse previewDefinition(String definition, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(400, "INVALID_PAGE", "Page must be nonnegative and size must be 1-100");
        }
        UUID tenantId = tenant();
        SegmentConditionQueryBuilder.SqlPredicate predicate = queryBuilder.build(definition);
        String fromWhere = " FROM contacts c WHERE c.tenant_id = :tenantId"
                + " AND c.status = 'ACTIVE' AND c.subscription_status = 'SUBSCRIBED'"
                + " AND " + predicate.sql();

        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*)" + fromWhere);
        bind(countQuery, tenantId, predicate);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query rowsQuery = entityManager.createNativeQuery(
                "SELECT c.*" + fromWhere + " ORDER BY c.created_at DESC LIMIT :limit OFFSET :offset",
                ContactEntity.class
        );
        bind(rowsQuery, tenantId, predicate);
        rowsQuery.setParameter("limit", size);
        rowsQuery.setParameter("offset", (long) page * size);

        @SuppressWarnings("unchecked")
        List<ContactEntity> rows = rowsQuery.getResultList();
        List<ContactResponse> items = rows.stream().map(this::response).toList();
        int totalPages = (int) Math.ceil((double) total / size);
        return new SegmentPreviewResponse(items, total, page, size, totalPages);
    }

    @Transactional(readOnly = true)
    public SegmentPreviewResponse previewSaved(UUID segmentId, int page, int size) {
        UUID tenantId = tenant();
        SegmentEntity segment = segments.findByIdAndTenantId(segmentId, tenantId)
                .orElseThrow(() -> new ApiException(404, "SEGMENT_NOT_FOUND", "Segment was not found"));
        return previewDefinition(segment.getDefinition(), page, size);
    }

    private static void bind(
            Query query,
            UUID tenantId,
            SegmentConditionQueryBuilder.SqlPredicate predicate
    ) {
        query.setParameter("tenantId", tenantId);
        predicate.parameters().forEach(query::setParameter);
    }

    private ContactResponse response(ContactEntity contact) {
        try {
            return new ContactResponse(
                contact.getId(),
                contact.getEmail(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getCompany(),
                contact.getPhone(),
                contact.getStatus(),
                contact.getSubscriptionStatus(),
                contact.getSource(),
                objectMapper.writeValueAsString(contact.getProperties()),
                contact.getCreatedAt(),
                contact.getUpdatedAt()
            );
        } catch (Exception exception) {
            throw new ApiException(500, "CONTACT_PROPERTIES_ERROR", "Unable to serialize contact properties");
        }
    }

    private static UUID tenant() {
        return TenantContext.get().orElseThrow(
                () -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required")
        );
    }
}