package com.texto.emailplatform.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plan_features")
public class PlanFeatureEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "feature_id", nullable = false)
    private UUID featureId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getFeatureId() {
        return featureId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
