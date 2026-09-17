"use client";

import Link from "next/link";
import { ArrowRight, ShieldAlert } from "lucide-react";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { TenantStatusBadge } from "@/components/admin/tenant-status-badge";
import { MetricDisplay } from "@/components/ops/metric-display";
import { SectionPanel } from "@/components/ops/section-panel";
import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  ADMIN_AUDIT,
  ADMIN_BILLING_ALERTS,
  ADMIN_COMPLIANCE,
  ADMIN_PLATFORM_STATS,
  ADMIN_TENANTS,
} from "@/lib/presentation/admin";
import { formatDateTime } from "@/lib/format";

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 }).format(value);
}

const QUICK_LINKS = [
  { href: "/admin/accounts/create", label: "Create account" },
  { href: "/admin/tenants", label: "Tenants" },
  { href: "/admin/billing", label: "Billing ops" },
  { href: "/admin/compliance", label: "Compliance" },
] as const;

export function AdminOverviewScreen() {
  const risky = ADMIN_TENANTS.filter((t) => t.risk === "high" || t.status === "suspended" || t.status === "past_due");
  const actionCount =
    ADMIN_PLATFORM_STATS.suspended + ADMIN_PLATFORM_STATS.pastDue + ADMIN_PLATFORM_STATS.openCompliance;

  return (
    <div className="flex min-h-full flex-col gap-4 lg:gap-5">
      <PageHeader
        eyebrow="Control plane"
        title="Platform operations"
        description="Health, billing risk, and compliance across every tenant."
        actions={
          <>
            <Button asChild size="sm" variant="secondary">
              <Link href="/admin/accounts/create">Create account</Link>
            </Button>
            <Button asChild size="sm">
              <Link href="/admin/tenants">Browse tenants</Link>
            </Button>
          </>
        }
      />
      <AdminPreviewBanner />

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <SectionPanel ambient className="p-4 sm:p-5">
          <MetricDisplay
            label="Tenants"
            value={ADMIN_PLATFORM_STATS.tenants.toLocaleString()}
            comparison={`${ADMIN_PLATFORM_STATS.active} active`}
            size="md"
          />
        </SectionPanel>
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay label="MRR" value={money(ADMIN_PLATFORM_STATS.mrr)} comparison="Preview catalog" size="md" />
        </SectionPanel>
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay
            label="Messages 24h"
            value={ADMIN_PLATFORM_STATS.messages24h.toLocaleString()}
            comparison={`${ADMIN_PLATFORM_STATS.platformDeliveryRate}% delivery`}
            size="md"
          />
        </SectionPanel>
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay
            label="Needs action"
            value={String(actionCount)}
            comparison={`${ADMIN_PLATFORM_STATS.pastDue} past due · ${ADMIN_PLATFORM_STATS.openCompliance} compliance`}
            size="md"
          />
        </SectionPanel>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {QUICK_LINKS.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className="panel flex items-center justify-between gap-3 rounded-2xl px-4 py-3.5 transition-colors hover:border-primary/30"
          >
            <span className="text-sm font-medium">{item.label}</span>
            <ArrowRight className="size-3.5 text-muted-foreground" />
          </Link>
        ))}
      </div>

      <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,20rem)]">
        <SectionPanel
          title="Tenant risk board"
          description="Accounts requiring operator attention"
          className="min-h-[240px] xl:min-h-0"
          action={
            <Button asChild variant="ghost" size="sm">
              <Link href="/admin/tenants">View all</Link>
            </Button>
          }
        >
          <div className="space-y-2">
            {risky.map((tenant) => (
              <Link
                key={tenant.id}
                href={`/admin/tenants/${tenant.id}`}
                className="flex flex-col gap-3 rounded-xl border border-border/70 bg-card/50 px-3 py-3 transition-colors hover:border-primary/30 hover:bg-primary/5 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{tenant.organization}</p>
                  <p className="mt-0.5 truncate font-mono text-[11px] text-muted-foreground">{tenant.ownerEmail}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-[11px] text-muted-foreground">{tenant.usagePct}% usage</span>
                  <Badge variant={tenant.risk === "high" ? "error" : "warning"}>{tenant.risk} risk</Badge>
                  <TenantStatusBadge status={tenant.status} />
                </div>
              </Link>
            ))}
          </div>
        </SectionPanel>

        <div className="flex min-w-0 flex-col gap-4">
          <SectionPanel
            title="Billing alerts"
            className="flex-1"
            action={
              <Button asChild variant="ghost" size="sm">
                <Link href="/admin/billing">Open</Link>
              </Button>
            }
          >
            <ul className="space-y-3">
              {ADMIN_BILLING_ALERTS.map((alert) => (
                <li key={alert.id} className="border-b border-border/60 pb-3 last:border-0 last:pb-0">
                  <p className="flex items-center gap-2 text-sm font-medium">
                    <ShieldAlert className="size-3.5 text-warning" />
                    {alert.organization}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">{alert.message}</p>
                </li>
              ))}
            </ul>
          </SectionPanel>
          <SectionPanel
            title="Compliance queue"
            className="flex-1"
            action={
              <Button asChild variant="ghost" size="sm">
                <Link href="/admin/compliance">Open</Link>
              </Button>
            }
          >
            <ul className="space-y-3">
              {ADMIN_COMPLIANCE.map((item) => (
                <li key={item.id}>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={item.severity === "high" ? "error" : "warning"}>{item.severity}</Badge>
                    <p className="text-sm font-medium">{item.organization}</p>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">{item.summary}</p>
                </li>
              ))}
            </ul>
          </SectionPanel>
        </div>
      </div>

      <SectionPanel
        title="Recent admin actions"
        description="Immutable audit stream (preview)"
        action={
          <Button asChild variant="ghost" size="sm">
            <Link href="/admin/audit">Full log</Link>
          </Button>
        }
      >
        <div className="-mx-1 overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr className="tech-label border-b border-border/70 text-muted-foreground">
                <th className="pb-2 font-medium">When</th>
                <th className="pb-2 font-medium">Actor</th>
                <th className="pb-2 font-medium">Action</th>
                <th className="pb-2 font-medium">Tenant</th>
                <th className="pb-2 font-medium">Detail</th>
              </tr>
            </thead>
            <tbody>
              {ADMIN_AUDIT.map((event) => (
                <tr key={event.id} className="border-b border-border/50 last:border-0">
                  <td className="py-2.5 font-mono text-xs whitespace-nowrap text-muted-foreground">
                    {formatDateTime(event.at)}
                  </td>
                  <td className="py-2.5 font-mono text-xs">{event.actor}</td>
                  <td className="py-2.5 font-mono text-xs text-primary">{event.action}</td>
                  <td className="py-2.5">{event.tenant}</td>
                  <td className="py-2.5 text-muted-foreground">{event.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </SectionPanel>
    </div>
  );
}
