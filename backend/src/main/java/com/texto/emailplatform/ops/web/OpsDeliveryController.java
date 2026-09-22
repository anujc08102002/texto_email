package com.texto.emailplatform.ops.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.ops.OpsCatalogService;
import com.texto.emailplatform.ops.OpsParse;
import com.texto.emailplatform.ops.api.OpsBounceEventResponse;
import com.texto.emailplatform.ops.api.OpsComplaintEventResponse;
import com.texto.emailplatform.ops.api.OpsDeliveryAttemptResponse;
import com.texto.emailplatform.ops.api.OpsEmailDetailResponse;
import com.texto.emailplatform.ops.api.OpsEmailSummaryResponse;
import com.texto.emailplatform.ops.api.OpsPageResponse;
import com.texto.emailplatform.ops.api.OpsSuppressionResponse;
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
@Tag(name = "Ops delivery")
public class OpsDeliveryController {

    private final OpsCatalogService opsCatalogService;

    public OpsDeliveryController(OpsCatalogService opsCatalogService) {
        this.opsCatalogService = opsCatalogService;
    }

    @GetMapping("/emails")
    @Operation(summary = "List email messages across tenants")
    public ApiResponse<OpsPageResponse<OpsEmailSummaryResponse>> emails(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String providerMessageId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.emails(
                OpsParse.uuid(tenantId, "tenantId"),
                status,
                sender,
                recipient,
                providerMessageId,
                OpsParse.uuid(messageId, "messageId"),
                from,
                to,
                page,
                size
        ));
    }

    @GetMapping("/emails/{messageId}")
    @Operation(summary = "Email message operations detail (no bodies or secrets)")
    public ApiResponse<OpsEmailDetailResponse> emailDetail(
            Authentication authentication,
            @PathVariable UUID messageId
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.emailDetail(messageId));
    }

    @GetMapping("/delivery-attempts")
    @Operation(summary = "List delivery attempts")
    public ApiResponse<OpsPageResponse<OpsDeliveryAttemptResponse>> deliveryAttempts(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String errorCategory,
            @RequestParam(required = false) Integer attemptNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.deliveryAttempts(
                OpsParse.uuid(tenantId, "tenantId"),
                OpsParse.uuid(messageId, "messageId"),
                status,
                errorCategory,
                attemptNumber,
                page,
                size
        ));
    }

    @GetMapping("/bounces")
    @Operation(summary = "List bounce events")
    public ApiResponse<OpsPageResponse<OpsBounceEventResponse>> bounces(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String bounceClass,
            @RequestParam(required = false) String correlationStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.bounces(
                OpsParse.uuid(tenantId, "tenantId"),
                bounceClass,
                correlationStatus,
                from,
                to,
                page,
                size
        ));
    }

    @GetMapping("/complaints")
    @Operation(summary = "List complaint events")
    public ApiResponse<OpsPageResponse<OpsComplaintEventResponse>> complaints(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String complaintType,
            @RequestParam(required = false) String processingStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.complaints(
                OpsParse.uuid(tenantId, "tenantId"),
                complaintType,
                processingStatus,
                from,
                to,
                page,
                size
        ));
    }

    @GetMapping("/suppressions")
    @Operation(summary = "List suppression entries")
    public ApiResponse<OpsPageResponse<OpsSuppressionResponse>> suppressions(
            Authentication authentication,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.suppressions(
                OpsParse.uuid(tenantId, "tenantId"),
                type,
                email,
                page,
                size
        ));
    }
}
