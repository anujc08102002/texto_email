package com.texto.emailplatform.suppression;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.common.security.PermissionAuthorizationManager;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.suppression.domain.SuppressionEntity;
import com.texto.emailplatform.suppression.domain.SuppressionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuppressionServiceUpsertTest {

    @Mock
    private SuppressionRepository suppressionRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private PermissionAuthorizationManager permissionAuthorizationManager;

    private SuppressionService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        service = new SuppressionService(suppressionRepository, entitlementService, permissionAuthorizationManager);
    }

    @Test
    void complaintDoesNotOverwriteManual() {
        SuppressionEntity manual = SuppressionEntity.create(
                tenantId,
                "alice@example.com",
                "alice@example.com",
                "manual",
                SuppressionEntity.TYPE_MANUAL,
                SuppressionEntity.SOURCE_USER,
                null
        );
        when(suppressionRepository.findByTenantIdAndNormalizedEmail(tenantId, "alice@example.com"))
                .thenReturn(Optional.of(manual));

        service.recordComplaint(tenantId, "alice@example.com", UUID.randomUUID(), "COMPLAINT");

        verify(suppressionRepository, never()).save(any());
    }

    @Test
    void complaintDoesNotOverwriteBounce() {
        SuppressionEntity bounce = SuppressionEntity.create(
                tenantId,
                "alice@example.com",
                "alice@example.com",
                "HARD_BOUNCE",
                SuppressionEntity.TYPE_BOUNCE,
                SuppressionEntity.SOURCE_SYSTEM,
                UUID.randomUUID()
        );
        when(suppressionRepository.findByTenantIdAndNormalizedEmail(tenantId, "alice@example.com"))
                .thenReturn(Optional.of(bounce));

        service.recordComplaint(tenantId, "alice@example.com", UUID.randomUUID(), "COMPLAINT");

        verify(suppressionRepository, never()).save(any());
    }
}
