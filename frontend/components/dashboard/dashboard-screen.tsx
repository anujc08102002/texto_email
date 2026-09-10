"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Activity, Mail, MousePointerClick, Percent, Send } from "lucide-react";
import { MetricCard } from "@/components/dashboard/metric-card";
import { PageHeader } from "@/components/layout/page-header";
import { UsageMeter } from "@/components/ops/usage-meter";
import { SectionPanel } from "@/components/ops/section-panel";
import { AnalyticsChart } from "@/components/analytics/analytics-chart";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useStoredUser } from "@/hooks/use-stored-user";
import { DASHBOARD_METRICS, PRESENTATION_NOTICE, WEEKLY_SERIES } from "@/lib/presentation";
import { getEntitlements, getSubscription, getUsage } from "@/services/platform";
import { listEmails } from "@/services/emails";
import type { EmailMessage, Entitlements, PlatformStatus, Subscription, UsageSnapshot } from "@/types/api";

function findMetric(usage: UsageSnapshot | null, code: string) {
  return usage?.metrics.find((item) => item.metric === code) ?? null;
}

const METRIC_ICONS = {
  sent: <Send className="size-4" />,
  delivery: <Activity className="size-4" />,
  bounce: <Percent className="size-4" />,
  open: <MousePointerClick className="size-4" />,
} as const;

export function DashboardScreen({ status }: { status: PlatformStatus | null }) {
  const user = useStoredUser();
  const [recentEmails, setRecentEmails] = useState<EmailMessage[]>([]);
  const [emailsLoading, setEmailsLoading] = useState(true);
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [entitlements, setEntitlements] = useState<Entitlements | null>(null);
  const [usage, setUsage] = useState<UsageSnapshot | null>(null);
  const [billingLoading, setBillingLoading] = useState(true);
  const now = useMemo(() => new Date(), []);
  const healthy = status?.status === "ok";

  useEffect(() => {
    let cancelled = false;
    listEmails({ size: 8 })
      .then((page) => {
        if (!cancelled) setRecentEmails(page.items);
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiClientError)) setRecentEmails([]);
      })
      .finally(() => {
        if (!cancelled) setEmailsLoading(false);
      });
    Promise.all([getSubscription(), getEntitlements(), getUsage()])
      .then(([sub, ents, use]) => {
        if (cancelled) return;
        setSubscription(sub);
        setEntitlements(ents);
        setUsage(use);
      })
      .catch(() => {
        /* billing endpoints unavailable — leave empty */
      })
      .finally(() => {
        if (!cancelled) setBillingLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const emails = findMetric(usage, "MONTHLY_EMAILS");
  const domains = findMetric(usage, "DOMAINS");
  const apiKeys = findMetric(usage, "API_KEYS");
  const templates = findMetric(usage, "TEMPLATES");

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Workspace"
        title={`Good ${now.getHours() < 12 ? "morning" : now.getHours() < 18 ? "afternoon" : "evening"}`}
        description={`${user?.organization ?? "Workspace"} · ${formatDateTime(now)}`}
        actions={
          <>
            <div className="inline-flex items-center gap-2 rounded-lg border border-border/80 bg-card px-3 py-1.5">
              <span className={`size-1.5 rounded-full ${healthy ? "bg-success" : "bg-muted-foreground"}`} />
              <span className="text-caption !text-[11px] font-medium">
                {healthy ? "Platform operational" : "Status unavailable"}
              </span>
            </div>
            <Button asChild>
              <Link href="/emails/new">Compose</Link>
            </Button>
          </>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {DASHBOARD_METRICS.map((metric, index) => (
          <MetricCard
            key={metric.id}
            metric={metric}
            icon={METRIC_ICONS[metric.id as keyof typeof METRIC_ICONS] ?? <Activity className="size-4" />}
            featured={index === 0}
          />
        ))}
      </div>
      <p className="text-caption">{PRESENTATION_NOTICE} Delivery metrics unlock with the email pipeline.</p>

      <div className="grid gap-4 xl:grid-cols-[1.4fr_0.9fr]">
        <AnalyticsChart
          title="Volume"
          description="Preview series · replace with GET /api/v1/analytics"
          data={WEEKLY_SERIES}
          preview
        />

        <div className="space-y-4">
          <SectionPanel title="Plan & subscription" description="Live from GET /api/v1/subscription">
            {billingLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-5 w-32" />
                <Skeleton className="h-4 w-48" />
              </div>
            ) : subscription ? (
              <div className="space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-section-heading">{subscription.planName}</p>
                  <span className="text-tech text-muted-foreground">{subscription.planCode}</span>
                  <span className="rounded-md border border-success/25 bg-success/10 px-2 py-0.5 text-[10px] font-semibold tracking-wide text-success uppercase">
                    {subscription.status}
                  </span>
                </div>
                <p className="text-caption">
                  Period {subscription.currentPeriodStart} → {subscription.currentPeriodEnd}
                </p>
                <Button asChild size="sm" variant="secondary">
                  <Link href="/billing">Manage billing</Link>
                </Button>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Subscription data unavailable.</p>
            )}
          </SectionPanel>

          <SectionPanel title="Monthly quota" description="Live from GET /api/v1/usage">
            {billingLoading ? (
              <Skeleton className="h-16 w-full" />
            ) : (
              <>
                <UsageMeter
                  label="Emails"
                  used={emails?.used ?? 0}
                  limit={emails?.limit ?? entitlements?.limits.MONTHLY_EMAILS ?? null}
                  footer={usage ? `Period ${usage.periodStart} → ${usage.periodEnd}` : undefined}
                />
                <div className="mt-4 grid grid-cols-3 gap-3 border-t border-border/70 pt-4 text-xs">
                  <div>
                    <p className="text-muted-foreground">Domains</p>
                    <p className="mt-1 text-tech">
                      {domains?.used ?? 0}/{domains?.limit ?? entitlements?.limits.DOMAINS ?? "∞"}
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">API keys</p>
                    <p className="mt-1 text-tech">
                      {apiKeys?.used ?? 0}/{apiKeys?.limit ?? entitlements?.limits.API_KEYS ?? "∞"}
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Templates</p>
                    <p className="mt-1 text-tech">
                      {templates?.used ?? 0}/{templates?.limit ?? entitlements?.limits.TEMPLATES ?? "∞"}
                    </p>
                  </div>
                </div>
              </>
            )}
          </SectionPanel>
        </div>
      </div>

      <SectionPanel
        title="Recent email activity"
        description="Loaded from GET /api/v1/emails"
        action={
          <Button asChild size="sm" variant="ghost">
            <Link href="/emails">View all</Link>
          </Button>
        }
      >
        {emailsLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : recentEmails.length === 0 ? (
          <EmptyState
            icon={<Mail className="size-5" />}
            title="Send your first email"
            description="Transactional activity for this workspace appears here."
            action={
              <Button asChild size="sm">
                <Link href="/emails/new">Compose</Link>
              </Button>
            }
          />
        ) : (
          <ul className="divide-y divide-border/70">
            {recentEmails.slice(0, 8).map((message) => (
              <li key={message.id} className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0">
                  <Link href={`/emails/${message.id}`} className="truncate text-sm font-medium hover:text-primary">
                    {message.subject}
                  </Link>
                  <p className="truncate text-tech text-muted-foreground">{message.recipient}</p>
                </div>
                <EmailStatusBadge status={message.status} />
              </li>
            ))}
          </ul>
        )}
      </SectionPanel>
    </div>
  );
}
