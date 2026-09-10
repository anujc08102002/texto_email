package com.texto.emailplatform.email.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.email.EmailService;
import com.texto.emailplatform.email.api.EmailMessageDetailResponse;
import com.texto.emailplatform.email.api.EmailMessagePageResponse;
import com.texto.emailplatform.email.api.EmailMessageResponse;
import com.texto.emailplatform.email.api.SendEmailRequest;
import com.texto.emailplatform.email.api.SendTestEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emails")
@Tag(name = "Emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping
    @Operation(summary = "List emails for the current tenant")
    public ApiResponse<EmailMessagePageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    ) {
        return ApiResponse.ok(emailService.list(page, size, status, q));
    }

    @GetMapping("/{messageId}")
    @Operation(summary = "Get a single email message with delivery attempts")
    public ApiResponse<EmailMessageDetailResponse> get(@PathVariable UUID messageId) {
        return ApiResponse.ok(emailService.get(messageId));
    }

    @PostMapping
    @Operation(summary = "Queue an email for async delivery (raw body or template)")
    public ApiResponse<EmailMessageResponse> send(
            @Valid @RequestBody SendEmailRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ApiResponse.ok(emailService.send(request, idempotencyKey));
    }

    @PostMapping("/test")
    @Operation(summary = "Queue a test email through the async delivery pipeline")
    public ApiResponse<EmailMessageResponse> sendTest(@Valid @RequestBody SendTestEmailRequest request) {
        return ApiResponse.ok(emailService.sendTest(request));
    }
}
