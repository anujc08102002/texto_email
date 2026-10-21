package com.texto.emailplatform.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.contact.domain.ContactEntity;
import com.texto.emailplatform.segment.domain.SegmentRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SegmentPreviewServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private EntityManager entityManager;

    @Mock
    private SegmentRepository segments;

    @Mock
    private Query countQuery;

    @Mock
    private Query rowsQuery;

    private SegmentPreviewService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new SegmentPreviewService(
                entityManager,
                new SegmentConditionQueryBuilder(new ObjectMapper()),
            segments,
            new ObjectMapper()
        );
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void previewBindsTenantAndReturnsOnlyActiveSubscribedAudienceQuery() {
        when(entityManager.createNativeQuery(contains("SELECT COUNT(*)"))).thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT c.*"), eq(ContactEntity.class)))
                .thenReturn(rowsQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(rowsQuery.getResultList()).thenReturn(List.of(ContactEntity.create(
                TENANT_ID,
                "a@example.com",
                "Ada",
                "Lovelace",
                "Example Co",
                null,
                "manual",
                java.util.Map.of()
        )));

        var response = service.previewDefinition(
                "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"company\",\"operator\":\"equals\",\"value\":\"Example Co\"}]}",
                0,
                10
        );

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rowsSql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(countSql.capture());
        verify(entityManager).createNativeQuery(rowsSql.capture(), eq(ContactEntity.class));
        assertThat(countSql.getValue()).contains("c.tenant_id = :tenantId", "c.status = 'ACTIVE'", "c.subscription_status = 'SUBSCRIBED'");
        assertThat(rowsSql.getValue()).contains("c.tenant_id = :tenantId", "LIMIT :limit OFFSET :offset");
        verify(countQuery).setParameter("tenantId", TENANT_ID);
        verify(rowsQuery).setParameter("tenantId", TENANT_ID);
        verify(rowsQuery).setParameter("limit", 10);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).extracting("email").containsExactly("a@example.com");
    }
}