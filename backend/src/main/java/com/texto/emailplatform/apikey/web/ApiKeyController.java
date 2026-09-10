package com.texto.emailplatform.apikey.web;

import com.texto.emailplatform.apikey.ApiKeyService;
import com.texto.emailplatform.apikey.api.ApiKeyResponse;
import com.texto.emailplatform.apikey.api.CreateApiKeyRequest;
import com.texto.emailplatform.apikey.api.CreatedApiKeyResponse;
import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.security.SecuritySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @Operation(summary = "Create an API key; the secret is returned only once")
    public ApiResponse<CreatedApiKeyResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        return ApiResponse.ok(apiKeyService.create(SecuritySupport.requireDashboardPrincipal(authentication), request));
    }

    @GetMapping
    @Operation(summary = "List API keys for the current tenant")
    public ApiResponse<List<ApiKeyResponse>> list(Authentication authentication) {
        return ApiResponse.ok(apiKeyService.list(SecuritySupport.requireDashboardPrincipal(authentication)));
    }

    @GetMapping("/{apiKeyId}")
    @Operation(summary = "Return a single API key")
    public ApiResponse<ApiKeyResponse> get(Authentication authentication, @PathVariable UUID apiKeyId) {
        return ApiResponse.ok(apiKeyService.get(SecuritySupport.requireDashboardPrincipal(authentication), apiKeyId));
    }

    @PostMapping("/{apiKeyId}/revoke")
    @Operation(summary = "Revoke an API key")
    public ApiResponse<ApiKeyResponse> revoke(Authentication authentication, @PathVariable UUID apiKeyId) {
        return ApiResponse.ok(apiKeyService.revoke(SecuritySupport.requireDashboardPrincipal(authentication), apiKeyId));
    }
}
