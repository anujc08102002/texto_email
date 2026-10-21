package com.texto.emailplatform.ops.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.delivery.mta.SesReadinessService;
import com.texto.emailplatform.ops.security.OpsAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/delivery/ses")
public class OpsSesReadinessController {
    private final SesReadinessService service;
    public OpsSesReadinessController(SesReadinessService service) { this.service = service; }
    @GetMapping("/readiness")
    public ApiResponse<SesReadinessService.Readiness> readiness(Authentication authentication,
            @RequestParam(defaultValue="") String identity) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(service.inspect(identity));
    }
}
