package com.texto.emailplatform.plan.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.plan.PlanService;
import com.texto.emailplatform.plan.api.PlanDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "List active subscription plans with features and limits")
    public ApiResponse<List<PlanDetailResponse>> listPlans() {
        return ApiResponse.ok(planService.listActive());
    }
}
