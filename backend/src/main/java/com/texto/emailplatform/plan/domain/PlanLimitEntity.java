package com.texto.emailplatform.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plan_limits")
public class PlanLimitEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "metric", nullable = false, length = 128)
    private String metric;

    /**
     * {@code null} means unlimited.
     */
    @Column(name = "limit_value")
    private Long limitValue;

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getMetric() {
        return metric;
    }

    public Long getLimitValue() {
        return limitValue;
    }
}
