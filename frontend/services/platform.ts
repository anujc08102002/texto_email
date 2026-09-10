import { apiGet } from "@/lib/api";
import type { Entitlements, Plan, PlatformStatus, Subscription, UsageSnapshot } from "@/types/api";

export function getPlans() {
  return apiGet<Plan[]>("/api/v1/plans");
}

export function getPlatformStatus() {
  return apiGet<PlatformStatus>("/api/v1/status");
}

export function getSubscription() {
  return apiGet<Subscription>("/api/v1/subscription");
}

export function getEntitlements() {
  return apiGet<Entitlements>("/api/v1/entitlements");
}

export function getUsage() {
  return apiGet<UsageSnapshot>("/api/v1/usage");
}
