package com.texto.emailplatform.billing.web;

import com.texto.emailplatform.billing.BillingWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/webhooks")
@Tag(name = "Billing webhooks")
public class RazorpayWebhookController {

    private final BillingWebhookService billingWebhookService;

    public RazorpayWebhookController(BillingWebhookService billingWebhookService) {
        this.billingWebhookService = billingWebhookService;
    }

    @PostMapping("/razorpay")
    @Operation(summary = "Receive Razorpay subscription webhooks")
    public ResponseEntity<Void> razorpay(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId
    ) {
        billingWebhookService.handleRazorpayWebhook(rawBody, signature, eventId);
        return ResponseEntity.ok().build();
    }
}
