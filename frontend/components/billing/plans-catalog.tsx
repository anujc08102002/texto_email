"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Check } from "lucide-react";
import { PlanCheckoutButton } from "@/components/billing/plan-checkout-button";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { getPlans, getSubscription } from "@/services/platform";
import type { Plan, Subscription } from "@/types/api";

export function PlansCatalog() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getPlans(), getSubscription().catch(() => null)])
      .then(([nextPlans, nextSubscription]) => {
        if (cancelled) return;
        setPlans(nextPlans);
        setSubscription(nextSubscription);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load plans.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Account"
        title="Plans"
        description="Database-driven catalog. Paid upgrades use Razorpay TEST checkout; entitlements follow webhooks."
        actions={
          <Button asChild variant="secondary" size="sm">
            <Link href="/billing">Back to billing</Link>
          </Button>
        }
      />
      {loading ? <LoadingState label="Loading plans" /> : null}
      {error ? <ErrorState description={error} /> : null}
      {!loading && !error ? (
        <SectionPanel title="Plan catalog" description="FREE is self-serve; ENTERPRISE is sales-assisted">
          <div className="grid gap-4 lg:grid-cols-2">
            {plans.map((plan) => {
              const enabledFeatures = Object.entries(plan.features ?? {})
                .filter(([, enabled]) => enabled)
                .map(([code]) => code);
              const limits = Object.entries(plan.limits ?? {});
              return (
                <div
                  key={plan.id}
                  className="flex h-full flex-col justify-between gap-4 rounded-2xl border border-border/70 bg-card/60 p-4 sm:p-5"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary">{plan.code}</p>
                      <Badge variant={plan.active ? "success" : "secondary"}>
                        {plan.active ? "Active" : "Inactive"}
                      </Badge>
                      {plan.code === subscription?.planCode ? <Badge variant="success">Current</Badge> : null}
                    </div>
                    <h2 className="mt-2 text-lg font-semibold tracking-tight">{plan.name}</h2>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">{plan.description}</p>
                    <div className="mt-4 grid gap-4 sm:grid-cols-2">
                      <div>
                        <p className="tech-label">Features</p>
                        <ul className="mt-2 space-y-1.5 text-sm text-muted-foreground">
                          {enabledFeatures.slice(0, 8).map((code) => (
                            <li key={code} className="flex items-center gap-2">
                              <Check className="size-3.5 text-primary" />
                              <span className="font-mono text-xs">{code}</span>
                            </li>
                          ))}
                          {enabledFeatures.length > 8 ? (
                            <li className="text-xs">+{enabledFeatures.length - 8} more</li>
                          ) : null}
                        </ul>
                      </div>
                      <div>
                        <p className="tech-label">Limits</p>
                        <ul className="mt-2 space-y-1.5 font-mono text-xs text-muted-foreground">
                          {limits.map(([metric, value]) => (
                            <li key={metric}>
                              {metric}: {value == null ? "unlimited" : value.toLocaleString()}
                            </li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  </div>
                  <div className="flex justify-end">
                    <PlanCheckoutButton planCode={plan.code} currentPlanCode={subscription?.planCode} />
                  </div>
                </div>
              );
            })}
            {plans.length === 0 ? (
              <p className="py-8 text-sm text-muted-foreground">No plans returned.</p>
            ) : null}
          </div>
        </SectionPanel>
      ) : null}
    </div>
  );
}
