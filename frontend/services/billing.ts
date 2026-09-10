import { apiGet, apiPost } from "@/lib/api";
import type { Subscription } from "@/types/api";

export type BillingConfig = {
  provider: string;
  configured: boolean;
  keyId: string | null;
};

export type CheckoutSession = {
  keyId: string;
  provider: string;
  subscriptionId: string;
  providerSubscriptionId: string;
  planCode: string;
  status: string;
};

export function getBillingConfig() {
  return apiGet<BillingConfig>("/api/v1/billing/config");
}

export function startCheckout(planCode: string) {
  return apiPost<CheckoutSession>("/api/v1/billing/checkout", { planCode });
}

export function cancelSubscription(atPeriodEnd: boolean) {
  return apiPost<Subscription>("/api/v1/billing/cancel", { atPeriodEnd });
}

export function pauseSubscription() {
  return apiPost<Subscription>("/api/v1/billing/pause", {});
}

export function resumeSubscription() {
  return apiPost<Subscription>("/api/v1/billing/resume", {});
}

export function changePlan(planCode: string) {
  return apiPost<Subscription>("/api/v1/billing/change-plan", { planCode });
}
