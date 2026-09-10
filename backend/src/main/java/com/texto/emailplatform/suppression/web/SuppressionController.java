package com.texto.emailplatform.suppression.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.suppression.api.CreateSuppressionRequest;
import com.texto.emailplatform.suppression.api.ImportSuppressionsRequest;
import com.texto.emailplatform.suppression.api.ImportSuppressionsResponse;
import com.texto.emailplatform.suppression.api.SuppressionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppressions")
@Tag(name = "Suppressions")
public class SuppressionController {

    private final SuppressionService suppressionService;

    public SuppressionController(SuppressionService suppressionService) {
        this.suppressionService = suppressionService;
    }

    @PostMapping
    @Operation(summary = "Manually suppress an email address")
    public ApiResponse<SuppressionResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateSuppressionRequest request
    ) {
        return ApiResponse.ok(suppressionService.createManual(
                SecuritySupport.requireDashboardPrincipal(authentication),
                request
        ));
    }

    @GetMapping
    @Operation(summary = "List suppressions with optional search/type filters")
    public ApiResponse<List<SuppressionResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type
    ) {
        return ApiResponse.ok(suppressionService.list(
                SecuritySupport.requireDashboardPrincipal(authentication),
                search,
                type
        ));
    }

    @GetMapping("/{suppressionId}")
    @Operation(summary = "Get a suppression")
    public ApiResponse<SuppressionResponse> get(Authentication authentication, @PathVariable UUID suppressionId) {
        return ApiResponse.ok(suppressionService.get(
                SecuritySupport.requireDashboardPrincipal(authentication),
                suppressionId
        ));
    }

    @DeleteMapping("/{suppressionId}")
    @Operation(summary = "Remove a suppression")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable UUID suppressionId) {
        suppressionService.delete(SecuritySupport.requireDashboardPrincipal(authentication), suppressionId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/import")
    @Operation(summary = "Import a list of email addresses into the suppression list")
    public ApiResponse<ImportSuppressionsResponse> importEmails(
            Authentication authentication,
            @Valid @RequestBody ImportSuppressionsRequest request
    ) {
        return ApiResponse.ok(suppressionService.importEmails(
                SecuritySupport.requireDashboardPrincipal(authentication),
                request
        ));
    }
}
