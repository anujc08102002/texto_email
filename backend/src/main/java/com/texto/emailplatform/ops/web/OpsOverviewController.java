package com.texto.emailplatform.ops.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.ops.OpsCatalogService;
import com.texto.emailplatform.ops.OpsParse;
import com.texto.emailplatform.ops.api.OpsDashboardResponse;
import com.texto.emailplatform.ops.api.OpsHealthResponse;
import com.texto.emailplatform.ops.api.OpsQueueStatusResponse;
import com.texto.emailplatform.ops.security.OpsAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Ops console")
public class OpsOverviewController {

    private final OpsCatalogService opsCatalogService;

    public OpsOverviewController(OpsCatalogService opsCatalogService) {
        this.opsCatalogService = opsCatalogService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Platform delivery summary")
    public ApiResponse<OpsDashboardResponse> dashboard(
            Authentication authentication,
            @RequestParam(required = false) String tenantId
    ) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.dashboard(OpsParse.uuid(tenantId, "tenantId")));
    }

    @GetMapping("/queues")
    @Operation(summary = "RabbitMQ queue depths for delivery workers")
    public ApiResponse<OpsQueueStatusResponse> queues(Authentication authentication) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.queues());
    }

    @GetMapping("/health")
    @Operation(summary = "Operations health snapshot")
    public ApiResponse<OpsHealthResponse> health(Authentication authentication) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(opsCatalogService.health());
    }
}
