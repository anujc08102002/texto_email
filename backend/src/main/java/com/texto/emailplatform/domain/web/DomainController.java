package com.texto.emailplatform.domain.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.domain.api.CreateDomainRequest;
import com.texto.emailplatform.domain.api.DomainResponse;
import com.texto.emailplatform.domain.api.DomainVerificationResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/domains")
@Tag(name = "Domains")
public class DomainController {

    private final DomainService domainService;

    public DomainController(DomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping
    @Operation(summary = "Register a sending domain")
    public ApiResponse<DomainResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateDomainRequest request
    ) {
        return ApiResponse.ok(domainService.create(SecuritySupport.requireDashboardPrincipal(authentication), request));
    }

    @GetMapping
    @Operation(summary = "List sending domains")
    public ApiResponse<List<DomainResponse>> list(Authentication authentication) {
        return ApiResponse.ok(domainService.list(SecuritySupport.requireDashboardPrincipal(authentication)));
    }

    @GetMapping("/{domainId}")
    @Operation(summary = "Get a sending domain")
    public ApiResponse<DomainResponse> get(Authentication authentication, @PathVariable UUID domainId) {
        return ApiResponse.ok(domainService.get(SecuritySupport.requireDashboardPrincipal(authentication), domainId));
    }

    @DeleteMapping("/{domainId}")
    @Operation(summary = "Delete a sending domain")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable UUID domainId) {
        domainService.delete(SecuritySupport.requireDashboardPrincipal(authentication), domainId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{domainId}/verify")
    @Operation(summary = "Verify DNS records for a domain")
    public ApiResponse<DomainResponse> verify(Authentication authentication, @PathVariable UUID domainId) {
        return ApiResponse.ok(domainService.verify(SecuritySupport.requireDashboardPrincipal(authentication), domainId));
    }

    @GetMapping("/{domainId}/verification")
    @Operation(summary = "Return DNS verification records (never includes private keys)")
    public ApiResponse<DomainVerificationResponse> verification(
            Authentication authentication,
            @PathVariable UUID domainId
    ) {
        return ApiResponse.ok(domainService.getVerification(
                SecuritySupport.requireDashboardPrincipal(authentication),
                domainId
        ));
    }
}
