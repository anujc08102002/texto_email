"use client";

import Link from "next/link";
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

export function AdminOverviewScreen() {
  const risky = ADMIN_TENANTS.filter((t) => t.risk === "high" || t.status === "suspended" || t.status === "past_due");

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Platform admin"
        title="Control plane"
        description="Cross-tenant operations: health, billing risk, compliance, and account controls."
        actions={
          <Button asChild size="sm">
            <Link href="/admin/tenants">Browse tenants</Link>
          </Button>
        }
      />
      <AdminPreviewBanner />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <SectionPanel ambient>
          <MetricDisplay label="Tenants" value={ADMIN_PLATFORM_STATS.tenants.toLocaleString()} comparison={`${ADMIN_PLATFORM_STATS.active} active`} size="md" />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay label="MRR" value={money(ADMIN_PLATFORM_STATS.mrr)} comparison="Preview catalog" size="md" />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay
            label="Messages 24h"
            value={ADMIN_PLATFORM_STATS.messages24h.toLocaleString()}
            comparison={`${ADMIN_PLATFORM_STATS.platformDeliveryRate}% delivery`}
            size="md"
          />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay
            label="Needs action"
            value={String(ADMIN_PLATFORM_STATS.suspended + ADMIN_PLATFORM_STATS.pastDue + ADMIN_PLATFORM_STATS.openCompliance)}
            comparison={`${ADMIN_PLATFORM_STATS.pastDue} past due · ${ADMIN_PLATFORM_STATS.openCompliance} compliance`}
            size="md"
          />
        </SectionPanel>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <SectionPanel title="Tenant risk board" description="Accounts requiring operator attention" action={
          <Button asChild variant="ghost" size="sm"><Link href="/admin/tenants">View all</Link></Button>
        }>
          <div className="space-y-2">
            {risky.map((tenant) => (
              <Link
                key={tenant.id}
                href={`/admin/tenants/${tenant.id}`}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/70 bg-card/50 px-3 py-3 transition-colors hover:border-primary/30 hover:bg-primary/5"
              >
                <div>
                  <p className="text-sm font-medium">{tenant.organization}</p>
                  <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">{tenant.ownerEmail}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={tenant.risk === "high" ? "error" : "warning"}>{tenant.risk} risk</Badge>
                  <TenantStatusBadge status={tenant.status} />
                </div>
              </Link>
            ))}
          </div>
        </SectionPanel>

        <div className="space-y-4">
          <SectionPanel title="Billing alerts" action={
            <Button asChild variant="ghost" size="sm"><Link href="/admin/billing">Open</Link></Button>
          }>
            <ul className="space-y-3">
              {ADMIN_BILLING_ALERTS.map((alert) => (
                <li key={alert.id} className="border-b border-border/60 pb-3 last:border-0 last:pb-0">
                  <p className="text-sm font-medium">{alert.organization}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{alert.message}</p>
                </li>
              ))}
            </ul>
          </SectionPanel>
          <SectionPanel title="Compliance queue" action={
            <Button asChild variant="ghost" size="sm"><Link href="/admin/compliance">Open</Link></Button>
          }>
            <ul className="space-y-3">
              {ADMIN_COMPLIANCE.map((item) => (
                <li key={item.id}>
                  <div className="flex items-center gap-2">
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

      <SectionPanel title="Recent admin actions" description="Immutable audit stream (preview)" action={
        <Button asChild variant="ghost" size="sm"><Link href="/admin/audit">Full log</Link></Button>
      }>
        <div className="overflow-x-auto">
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
                  <td className="py-2.5 font-mono text-xs text-muted-foreground">{formatDateTime(event.at)}</td>
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
