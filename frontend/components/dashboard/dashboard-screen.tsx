"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  ArrowRight,
  BarChart3,
  Globe,
  KeyRound,
  LayoutTemplate,
  Mail,
  MousePointerClick,
  Percent,
  Send,
} from "lucide-react";
import { MetricCard } from "@/components/dashboard/metric-card";
import { PageHeader } from "@/components/layout/page-header";
import { UsageMeter } from "@/components/ops/usage-meter";
import { DeliveryHealth } from "@/components/ops/delivery-health";
import { SectionPanel } from "@/components/ops/section-panel";
import { AnalyticsChart } from "@/components/analytics/analytics-chart";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiClientError } from "@/lib/api";
import { formatDateTime, formatRelativeTime } from "@/lib/format";
import { useStoredUser } from "@/hooks/use-stored-user";
import { DASHBOARD_METRICS, DELIVERY_BREAKDOWN, PRESENTATION_NOTICE, WEEKLY_SERIES } from "@/lib/presentation";
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

const QUICK_LINKS = [
  { href: "/domains", label: "Domains", icon: Globe },
  { href: "/api-keys", label: "API keys", icon: KeyRound },
  { href: "/templates", label: "Templates", icon: LayoutTemplate },
  { href: "/analytics", label: "Analytics", icon: BarChart3 },
] as const;

function greetingFor(now: Date) {
  const hour = now.getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

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
  const emailLimit = emails?.limit ?? entitlements?.limits.MONTHLY_EMAILS ?? null;
  const emailPct = emailLimit && emailLimit > 0 ? (emails?.used ?? 0) / emailLimit : 0;

  const attention = useMemo(() => {
    const items: Array<{ title: string; detail: string; href: string }> = [];
    if (!healthy) {
      items.push({ title: "API unreachable", detail: "Platform status is not ok.", href: "/dashboard" });
    }
    if (!billingLoading && emailLimit && emailPct >= 0.8) {
      items.push({
        title: "Quota is running high",
        detail: `${Math.round(emailPct * 100)}% of monthly emails used.`,
        href: "/billing",
      });
    }
    if (!emailsLoading && recentEmails.length === 0) {
      items.push({
        title: "No delivery yet",
        detail: "Queue a message to see activity in this workspace.",
        href: "/emails/new",
      });
    }
    return items.slice(0, 3);
  }, [billingLoading, emailLimit, emailPct, emailsLoading, healthy, recentEmails.length]);

  return (
    <div className="flex min-h-full flex-col gap-4 lg:gap-5">
      <PageHeader
        eyebrow="Overview"
        title={user?.organization ?? "Workspace"}
        description={`${greetingFor(now)} · ${formatDateTime(now)}`}
        actions={
          <>
            <Button asChild>
              <Link href="/emails/new">Compose</Link>
            </Button>
            <div className="hidden items-center gap-2 rounded-full border border-border/80 bg-card px-3 py-1.5 xl:inline-flex">
              <span className={`size-1.5 rounded-full ${healthy ? "bg-success" : "bg-muted-foreground"}`} />
              <span className="text-caption !text-[11px] font-medium">
                {healthy ? "Platform operational" : "Status unavailable"}
              </span>
            </div>
          </>
        }
      />

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        {DASHBOARD_METRICS.map((metric, index) => (
          <MetricCard
            key={metric.id}
            metric={metric}
            icon={METRIC_ICONS[metric.id as keyof typeof METRIC_ICONS] ?? <Activity className="size-4" />}
            featured={index === 0}
          />
        ))}
      </div>
      <p className="text-caption -mt-1 hidden sm:block">{PRESENTATION_NOTICE}</p>

      <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,20rem)]">
        <AnalyticsChart
          title="Volume"
          description="Weekly send and delivery"
          data={WEEKLY_SERIES}
          preview
          fill
          className="min-w-0"
        />

        <div className="flex min-w-0 flex-col gap-4">
          {attention.length > 0 ? (
            <SectionPanel title="Needs attention" className="shrink-0">
              <ul className="space-y-2">
                {attention.map((item) => (
                  <li key={item.title}>
                    <Link
                      href={item.href}
                      className="flex items-start justify-between gap-3 rounded-xl border border-border/70 bg-card/60 px-3 py-2.5 transition-colors hover:border-primary/30 hover:bg-primary/5"
                    >
                      <span className="min-w-0">
                        <span className="block text-sm font-medium">{item.title}</span>
                        <span className="mt-0.5 block text-xs text-muted-foreground">{item.detail}</span>
                      </span>
                      <ArrowRight className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />
                    </Link>
                  </li>
                ))}
              </ul>
            </SectionPanel>
          ) : null}

          <SectionPanel title="Plan" description="Live from GET /api/v1/subscription" className="flex-1">
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

          <SectionPanel title="Monthly quota" description="Live from GET /api/v1/usage" className="flex-1">
            {billingLoading ? (
              <Skeleton className="h-16 w-full" />
            ) : (
              <>
                <UsageMeter
                  label="Emails"
                  used={emails?.used ?? 0}
                  limit={emailLimit}
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

          <SectionPanel title="Delivery mix" className="flex-1">
            <DeliveryHealth breakdown={DELIVERY_BREAKDOWN} />
          </SectionPanel>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {QUICK_LINKS.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className="panel flex items-center justify-between gap-3 rounded-2xl px-4 py-3.5 transition-colors hover:border-primary/30"
          >
            <span className="flex min-w-0 items-center gap-3">
              <span className="flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <item.icon className="size-4" />
              </span>
              <span className="text-sm font-medium">{item.label}</span>
            </span>
            <ArrowRight className="size-3.5 text-muted-foreground" />
          </Link>
        ))}
      </div>

      <SectionPanel
        title="Recent email activity"
        description="Loaded from GET /api/v1/emails"
        className="min-h-[220px]"
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
              <li key={message.id} className="py-3 first:pt-0 last:pb-0">
                <Link href={`/emails/${message.id}`} className="flex items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium hover:text-primary">{message.subject}</p>
                    <p className="truncate text-sm text-muted-foreground">{message.recipient}</p>
                  </div>
                  <p className="hidden shrink-0 text-xs text-muted-foreground sm:block">
                    {formatRelativeTime(message.createdAt)}
                  </p>
                  <EmailStatusBadge status={message.status} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </SectionPanel>
    </div>
  );
}
