package com.texto.emailplatform.analytics.web;

import com.texto.emailplatform.analytics.AnalyticsService;
import com.texto.emailplatform.analytics.api.AnalyticsResponse;
import com.texto.emailplatform.common.api.ApiResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    public AnalyticsController(AnalyticsService analyticsService) { this.analyticsService = analyticsService; }

    @GetMapping
    public ApiResponse<AnalyticsResponse> get(@RequestParam(required = false) Instant from,
                                               @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(analyticsService.get(from, to));
    }
}
