package com.texto.emailplatform.ops.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.ops.OpsCatalogService;
import com.texto.emailplatform.ops.OpsParse;
import com.texto.emailplatform.ops.api.OpsApiKeyResponse;
import com.texto.emailplatform.ops.api.OpsAuditEventResponse;
import com.texto.emailplatform.ops.api.OpsDomainResponse;
import com.texto.emailplatform.ops.api.OpsHttpRequestAuditResponse;
import com.texto.emailplatform.ops.api.OpsOutboxEventResponse;
import com.texto.emailplatform.ops.api.OpsPageResponse;
import com.texto.emailplatform.ops.api.OpsTenantResponse;
import com.texto.emailplatform.ops.api.OpsTenantUsageResponse;
import com.texto.emailplatform.ops.api.OpsUserResponse;
import com.texto.emailplatform.ops.api.OpsWebhookEventResponse;
import com.texto.emailplatform.ops.security.OpsAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Ops directory")
public class OpsDirectoryController {

    private final OpsCatalogService opsCatalogService;

    public OpsDirectoryController(OpsCatalogService opsCatalogService) {
        this.opsCatalogService = opsCatalogService;
    }

    @GetMapping("/outbox")
    @Operation(summary = "List outbox events")
    public ApiResponse<OpsPageResponse<OpsOutboxEventResponse>> outbox(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.outbox(OpsParse.uuid(tenantId, "tenantId"), status, page, size));
    }

    @GetMapping("/domains")
    @Operation(summary = "List sending domains and DNS verification status")
    public ApiResponse<OpsPageResponse<OpsDomainResponse>> domains(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.domains(OpsParse.uuid(tenantId, "tenantId"), status, domain, page, size));
    }

    @GetMapping("/tenants")
    @Operation(summary = "List tenants")
    public ApiResponse<OpsPageResponse<OpsTenantResponse>> tenants(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.tenants(status, q, page, size));
    }

    @GetMapping("/users")
    @Operation(summary = "List tenant users (no password hashes)")
    public ApiResponse<OpsPageResponse<OpsUserResponse>> users(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.users(OpsParse.uuid(tenantId, "tenantId"), role, status, page, size));
    }

    @GetMapping("/api-keys")
    @Operation(summary = "List API key metadata (never raw secrets)")
    public ApiResponse<OpsPageResponse<OpsApiKeyResponse>> apiKeys(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.apiKeys(
                OpsParse.uuid(tenantId, "tenantId"),
                status,
                environment,
                page,
                size
        ));
    }

    @GetMapping("/webhooks/events")
    @Operation(summary = "List webhook delivery history without payloads")
    public ApiResponse<OpsPageResponse<OpsWebhookEventResponse>> webhookEvents(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.webhookEvents(
                OpsParse.uuid(tenantId, "tenantId"),
                status,
                eventType,
                page,
                size
        ));
    }

    @GetMapping("/audit")
    @Operation(summary = "List platform-admin request audit events")
    public ApiResponse<OpsPageResponse<OpsAuditEventResponse>> audit(
            Authentication authentication,
            @RequestParam(required = false) String adminId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.audit(OpsParse.uuid(adminId, "adminId"), from, to, page, size));
    }

    @GetMapping("/http-request-audit")
    @Operation(summary = "List API HTTP request audit events without bodies or credentials")
    public ApiResponse<OpsPageResponse<OpsHttpRequestAuditResponse>> httpRequestAudit(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.httpRequestAudit(
                OpsParse.uuid(tenantId, "tenantId"),
                requestId,
                statusCode,
                route,
                from,
                to,
                page,
                size
        ));
    }

    @GetMapping("/tenants/{tenantId}/usage")
    @Operation(summary = "Tenant plan, entitlement and usage snapshot")
    public ApiResponse<OpsTenantUsageResponse> usage(Authentication authentication, @PathVariable UUID tenantId) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.usage(tenantId));
    }
}
