"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { SectionPanel } from "@/components/ops/section-panel";
import { UsageMeter } from "@/components/ops/usage-meter";
import { PlanCheckoutButton } from "@/components/billing/plan-checkout-button";
import { ApiClientError } from "@/lib/api";
import { cancelSubscription, getBillingConfig, type BillingConfig } from "@/services/billing";
import { getEntitlements, getSubscription, getUsage } from "@/services/platform";
import type { Entitlements, Plan, Subscription, UsageSnapshot } from "@/types/api";
import { cn } from "@/lib/utils";

function metric(usage: UsageSnapshot | null, code: string) {
  return usage?.metrics.find((item) => item.metric === code) ?? null;
}

export function BillingOverview({ plans }: { plans: Plan[] }) {
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [entitlements, setEntitlements] = useState<Entitlements | null>(null);
  const [usage, setUsage] = useState<UsageSnapshot | null>(null);
  const [billingConfig, setBillingConfig] = useState<BillingConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const refresh = useCallback(async () => {
    const [sub, ents, use, config] = await Promise.all([
      getSubscription(),
      getEntitlements(),
      getUsage(),
      getBillingConfig(),
    ]);
    setSubscription(sub);
    setEntitlements(ents);
    setUsage(use);
    setBillingConfig(config);
  }, []);

  useEffect(() => {
    let cancelled = false;
    refresh()
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load billing data.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  async function onCancelAtPeriodEnd() {
    setCancelling(true);
    try {
      const updated = await cancelSubscription(true);
      setSubscription(updated);
      toast.success("Cancellation scheduled at period end");
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to cancel subscription");
    } finally {
      setCancelling(false);
    }
  }

  if (loading) return <LoadingState label="Loading subscription" />;
  if (error) return <ErrorState description={error} />;

  const emails = metric(usage, "MONTHLY_EMAILS");
  const domains = metric(usage, "DOMAINS");
  const apiKeys = metric(usage, "API_KEYS");
  const templates = metric(usage, "TEMPLATES");
  const hasPaidProvider = Boolean(subscription?.providerSubscriptionId);

  return (
    <div className="space-y-4">
      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <SectionPanel title="Current plan" description="Internal subscription is the source of truth for entitlements">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <p className="tech-label">Plan</p>
              <p className="mt-1 text-xl font-semibold tracking-tight">{subscription?.planName ?? "—"}</p>
              <p className="mt-1 font-mono text-xs text-muted-foreground">{subscription?.planCode ?? "—"}</p>
            </div>
            <div>
              <p className="tech-label">Status</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <Badge variant={subscription?.status === "ACTIVE" ? "success" : "secondary"}>
                  {subscription?.status ?? "—"}
                </Badge>
                {subscription?.pendingPlanCode ? (
                  <Badge variant="secondary">Pending {subscription.pendingPlanCode}</Badge>
                ) : null}
              </div>
            </div>
            <div>
              <p className="tech-label">Billing period</p>
              <p className="mt-1 font-mono text-xs">
                {subscription
                  ? `${new Date(subscription.currentPeriodStart).toLocaleDateString()} → ${new Date(subscription.currentPeriodEnd).toLocaleDateString()}`
                  : "—"}
              </p>
            </div>
            <div>
              <p className="tech-label">Provider</p>
              <p className="mt-1 font-mono text-xs text-muted-foreground">
                {subscription?.provider ?? billingConfig?.provider ?? "—"}
                {billingConfig?.configured ? " · test mode ready" : " · not configured"}
              </p>
            </div>
            <div>
              <p className="tech-label">Cancel at period end</p>
              <p className="mt-1 text-sm">{subscription?.cancelAtPeriodEnd ? "Yes" : "No"}</p>
            </div>
            <div>
              <p className="tech-label">Grace ends</p>
              <p className="mt-1 font-mono text-xs">
                {subscription?.gracePeriodEndsAt
                  ? new Date(subscription.gracePeriodEndsAt).toLocaleString()
                  : "—"}
              </p>
            </div>
          </div>
          {hasPaidProvider && !subscription?.cancelAtPeriodEnd ? (
            <div className="mt-4 border-t border-border/70 pt-4">
              <Button variant="secondary" size="sm" disabled={cancelling} onClick={onCancelAtPeriodEnd}>
                {cancelling ? "Scheduling…" : "Cancel at period end"}
              </Button>
            </div>
          ) : null}
        </SectionPanel>

        <SectionPanel title="Entitlements & usage" description="GET /api/v1/entitlements · /usage">
          <UsageMeter
            label="Messages"
            used={emails?.used ?? 0}
            limit={emails?.limit ?? entitlements?.limits.MONTHLY_EMAILS ?? null}
            footer={usage ? `Period ${usage.periodStart} → ${usage.periodEnd}` : undefined}
          />
          <div className="mt-4 grid grid-cols-3 gap-3 border-t border-border/70 pt-4">
            {[
              { label: "Domains", item: domains, fallback: entitlements?.limits.DOMAINS },
              { label: "API keys", item: apiKeys, fallback: entitlements?.limits.API_KEYS },
              { label: "Templates", item: templates, fallback: entitlements?.limits.TEMPLATES },
            ].map((row) => (
              <div key={row.label}>
                <p className="tech-label">{row.label}</p>
                <p className="mt-1 font-mono text-sm tabular-nums">
                  {row.item?.used ?? 0}
                  <span className="text-muted-foreground">
                    {" "}
                    / {row.item?.limit ?? row.fallback ?? "∞"}
                  </span>
                </p>
              </div>
            ))}
          </div>
        </SectionPanel>
      </div>

      <SectionPanel
        title="Available plans"
        description="Upgrade uses Razorpay TEST checkout; entitlements change only after webhook confirmation"
      >
        <div className="divide-y divide-border/70">
          {plans.map((plan, index) => {
            const current = plan.code === subscription?.planCode;
            return (
              <div
                key={plan.id}
                className={cn("flex flex-wrap items-center justify-between gap-3 py-4", index === 0 && "pt-0")}
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="font-mono text-[11px] uppercase tracking-wide text-primary">{plan.code}</p>
                    {current ? <Badge variant="success">Current</Badge> : null}
                  </div>
                  <p className="mt-1 text-sm font-semibold">{plan.name}</p>
                  <p className="mt-1 max-w-xl text-sm text-muted-foreground">{plan.description}</p>
                </div>
                <PlanCheckoutButton
                  planCode={plan.code}
                  currentPlanCode={subscription?.planCode}
                  onCompleted={() => {
                    void refresh().catch(() => undefined);
                  }}
                />
              </div>
            );
          })}
        </div>
        <Button asChild variant="ghost" size="sm" className="mt-2">
          <Link href="/plans">Open full plan comparison</Link>
        </Button>
      </SectionPanel>
    </div>
  );
}
