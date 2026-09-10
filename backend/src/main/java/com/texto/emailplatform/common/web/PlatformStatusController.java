package com.texto.emailplatform.common.web;

import com.texto.emailplatform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
@Tag(name = "Status")
public class PlatformStatusController {

    @GetMapping
    @Operation(summary = "Platform status")
    public ApiResponse<PlatformStatusResponse> status() {
        return ApiResponse.ok(new PlatformStatusResponse("email-platform", "0.1.0", "ok"));
    }

    public record PlatformStatusResponse(String name, String version, String status) {
    }
}
