package com.texto.emailplatform.campaign.web;

import com.texto.emailplatform.campaign.CampaignUnsubscribeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/unsubscribe/{token:[a-f0-9]{64}}")
public class CampaignUnsubscribeController {
    private final CampaignUnsubscribeService service;
    public CampaignUnsubscribeController(CampaignUnsubscribeService service) { this.service = service; }

    // GET is deliberately read-only: email security scanners must not unsubscribe a recipient.
    @GetMapping(produces = "text/html")
    public ResponseEntity<String> confirmation(@PathVariable String token) {
        return page("<h1>Unsubscribe from emails</h1><p>Confirm to stop receiving emails from this sender.</p>"
                + "<form method=\"post\"><button type=\"submit\">Unsubscribe</button></form>");
    }

    @PostMapping(produces = "text/html")
    public ResponseEntity<String> unsubscribe(@PathVariable String token) {
        service.unsubscribe(token);
        return page("<h1>You are unsubscribed</h1><p>Your email preferences have been updated.</p>");
    }

    private static ResponseEntity<String> page(String content) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; form-action 'self'; frame-ancestors 'none'")
                .body("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Email preferences</title><body><main>" + content + "</main></body></html>");
    }
}
