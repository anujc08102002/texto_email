package com.texto.emailplatform.webhook.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.webhook.WebhookService;
import com.texto.emailplatform.webhook.api.CreateWebhookRequest;
import com.texto.emailplatform.webhook.api.CreatedWebhookResponse;
import com.texto.emailplatform.webhook.api.PatchWebhookRequest;
import com.texto.emailplatform.webhook.api.WebhookConfigResponse;
import com.texto.emailplatform.webhook.api.WebhookEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @Operation(summary = "Create a webhook; signing secret is returned only once")
    public ApiResponse<CreatedWebhookResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateWebhookRequest request
    ) {
        return ApiResponse.ok(webhookService.create(SecuritySupport.requireDashboardPrincipal(authentication), request));
    }

    @GetMapping
    @Operation(summary = "List webhooks")
    public ApiResponse<List<WebhookConfigResponse>> list(Authentication authentication) {
        return ApiResponse.ok(webhookService.list(SecuritySupport.requireDashboardPrincipal(authentication)));
    }

    @GetMapping("/{webhookId}")
    @Operation(summary = "Get a webhook (secret never included)")
    public ApiResponse<WebhookConfigResponse> get(Authentication authentication, @PathVariable UUID webhookId) {
        return ApiResponse.ok(webhookService.get(SecuritySupport.requireDashboardPrincipal(authentication), webhookId));
    }

    @PatchMapping("/{webhookId}")
    @Operation(summary = "Update webhook URL, description, or event subscriptions")
    public ApiResponse<WebhookConfigResponse> patch(
            Authentication authentication,
            @PathVariable UUID webhookId,
            @Valid @RequestBody PatchWebhookRequest request
    ) {
        return ApiResponse.ok(webhookService.patch(
                SecuritySupport.requireDashboardPrincipal(authentication),
                webhookId,
                request
        ));
    }

    @PostMapping("/{webhookId}/pause")
    @Operation(summary = "Pause webhook delivery")
    public ApiResponse<WebhookConfigResponse> pause(Authentication authentication, @PathVariable UUID webhookId) {
        return ApiResponse.ok(webhookService.pause(SecuritySupport.requireDashboardPrincipal(authentication), webhookId));
    }

    @PostMapping("/{webhookId}/resume")
    @Operation(summary = "Resume webhook delivery")
    public ApiResponse<WebhookConfigResponse> resume(Authentication authentication, @PathVariable UUID webhookId) {
        return ApiResponse.ok(webhookService.resume(SecuritySupport.requireDashboardPrincipal(authentication), webhookId));
    }

    @PostMapping("/{webhookId}/rotate-secret")
    @Operation(summary = "Rotate the signing secret; new secret is returned only once")
    public ApiResponse<CreatedWebhookResponse> rotateSecret(
            Authentication authentication,
            @PathVariable UUID webhookId
    ) {
        return ApiResponse.ok(webhookService.rotateSecret(
                SecuritySupport.requireDashboardPrincipal(authentication),
                webhookId
        ));
    }

    @DeleteMapping("/{webhookId}")
    @Operation(summary = "Disable a webhook")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable UUID webhookId) {
        webhookService.delete(SecuritySupport.requireDashboardPrincipal(authentication), webhookId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{webhookId}/events")
    @Operation(summary = "List delivery attempts/events for a webhook")
    public ApiResponse<List<WebhookEventResponse>> events(
            Authentication authentication,
            @PathVariable UUID webhookId
    ) {
        return ApiResponse.ok(webhookService.listEvents(
                SecuritySupport.requireDashboardPrincipal(authentication),
                webhookId
        ));
    }

    @PostMapping("/{webhookId}/test")
    @Operation(summary = "Enqueue a controlled test webhook event")
    public ApiResponse<WebhookEventResponse> test(Authentication authentication, @PathVariable UUID webhookId) {
        return ApiResponse.ok(webhookService.sendTest(
                SecuritySupport.requireDashboardPrincipal(authentication),
                webhookId
        ));
    }
}
