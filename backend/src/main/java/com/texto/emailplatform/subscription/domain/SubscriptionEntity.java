package com.texto.emailplatform.subscription.domain;

import com.texto.emailplatform.subscription.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "trial_start")
    private Instant trialStart;

    @Column(name = "trial_end")
    private Instant trialEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "provider_customer_id", length = 128)
    private String providerCustomerId;

    @Column(name = "provider_subscription_id", length = 128)
    private String providerSubscriptionId;

    @Column(name = "provider_status", length = 64)
    private String providerStatus;

    @Column(name = "pending_plan_id")
    private UUID pendingPlanId;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    // Timestamp (provider event created_at) of the most recent billing webhook applied to this
    // subscription. Used to ignore out-of-order webhook deliveries (Razorpay does not guarantee
    // event ordering).
    @Column(name = "last_billing_event_at")
    private Instant lastBillingEventAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SubscriptionEntity create(
            UUID tenantId,
            UUID planId,
            String status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {
        Instant now = Instant.now();
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.planId = planId;
        entity.status = status;
        entity.currentPeriodStart = currentPeriodStart;
        entity.currentPeriodEnd = currentPeriodEnd;
        entity.cancelAtPeriodEnd = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void changeStatus(String newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void changePlan(UUID newPlanId) {
        this.planId = newPlanId;
        this.updatedAt = Instant.now();
    }

    public void cancel(boolean atPeriodEnd) {
        Instant now = Instant.now();
        this.cancelAtPeriodEnd = atPeriodEnd;
        if (!atPeriodEnd) {
            this.status = SubscriptionStatus.CANCELLED;
            this.cancelledAt = now;
        }
        this.updatedAt = now;
    }

    public void attachProvider(
            String providerName,
            String providerCustomerId,
            String providerSubscriptionId,
            String providerStatus
    ) {
        this.provider = providerName;
        this.providerCustomerId = providerCustomerId;
        this.providerSubscriptionId = providerSubscriptionId;
        this.providerStatus = providerStatus;
        this.updatedAt = Instant.now();
    }

    public void updateProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
        this.updatedAt = Instant.now();
    }

    public void setPendingPlan(UUID planId) {
        this.pendingPlanId = planId;
        this.updatedAt = Instant.now();
    }

    public void clearPendingPlan() {
        this.pendingPlanId = null;
        this.updatedAt = Instant.now();
    }

    public void applyPeriod(Instant periodStart, Instant periodEnd) {
        if (periodStart != null) {
            this.currentPeriodStart = periodStart;
        }
        if (periodEnd != null) {
            this.currentPeriodEnd = periodEnd;
        }
        this.updatedAt = Instant.now();
    }

    public void markGrace(Instant gracePeriodEndsAt) {
        this.status = SubscriptionStatus.GRACE_PERIOD;
        this.gracePeriodEndsAt = gracePeriodEndsAt;
        this.updatedAt = Instant.now();
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
        this.updatedAt = Instant.now();
    }

    public void setGracePeriodEndsAt(Instant gracePeriodEndsAt) {
        this.gracePeriodEndsAt = gracePeriodEndsAt;
        this.updatedAt = Instant.now();
    }

    public void clearGrace() {
        this.gracePeriodEndsAt = null;
        this.updatedAt = Instant.now();
    }

    public void activatePendingPlan(UUID newPlanId) {
        this.planId = newPlanId;
        this.pendingPlanId = null;
        this.status = SubscriptionStatus.ACTIVE;
        this.cancelAtPeriodEnd = false;
        this.cancelledAt = null;
        this.gracePeriodEndsAt = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getTrialStart() {
        return trialStart;
    }

    public Instant getTrialEnd() {
        return trialEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderCustomerId() {
        return providerCustomerId;
    }

    public String getProviderSubscriptionId() {
        return providerSubscriptionId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public UUID getPendingPlanId() {
        return pendingPlanId;
    }

    public Instant getGracePeriodEndsAt() {
        return gracePeriodEndsAt;
    }

    public Instant getLastBillingEventAt() {
        return lastBillingEventAt;
    }

    /** Advances the last-applied billing-event watermark, never moving it backwards. */
    public void recordBillingEventAt(Instant eventCreatedAt) {
        if (eventCreatedAt == null) {
            return;
        }
        if (lastBillingEventAt == null || eventCreatedAt.isAfter(lastBillingEventAt)) {
            lastBillingEventAt = eventCreatedAt;
            this.updatedAt = Instant.now();
        }
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
