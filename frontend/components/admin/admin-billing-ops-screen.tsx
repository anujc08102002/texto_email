"use client";

import Link from "next/link";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { previewAdminAction } from "@/lib/admin/actions";
import { ADMIN_BILLING_ALERTS, ADMIN_TENANTS } from "@/lib/presentation/admin";

export function AdminBillingOpsScreen() {
  const pastDue = ADMIN_TENANTS.filter((t) => t.status === "past_due");

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Platform admin"
        title="Billing operations"
        description="Dunning, credits, and payment risk across the tenant fleet."
      />
      <AdminPreviewBanner />

      <div className="grid gap-4 xl:grid-cols-2">
        <SectionPanel title="Past due tenants" description="Accounts with unpaid invoices">
          <div className="space-y-2">
            {pastDue.map((tenant) => (
              <div key={tenant.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/70 px-3 py-3">
                <div>
                  <p className="text-sm font-medium">{tenant.organization}</p>
                  <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">${tenant.mrr}/mo · {tenant.planName}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => previewAdminAction("billing.send_reminder", tenant.organization)}>
                    Remind
                  </Button>
                  <Button asChild size="sm" variant="secondary">
                    <Link href={`/admin/tenants/${tenant.id}`}>Open</Link>
                  </Button>
                </div>
              </div>
            ))}
            {pastDue.length === 0 ? <p className="text-sm text-muted-foreground">No past-due tenants in preview set.</p> : null}
          </div>
        </SectionPanel>

        <SectionPanel title="Alert stream" description="Billing events requiring review">
          <ul className="space-y-3">
            {ADMIN_BILLING_ALERTS.map((alert) => (
              <li key={alert.id} className="rounded-xl border border-border/70 px-3 py-3">
                <div className="flex items-center gap-2">
                  <Badge variant="warning">{alert.kind.replaceAll("_", " ")}</Badge>
                  <p className="text-sm font-medium">{alert.organization}</p>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">{alert.message}</p>
                {alert.amount ? (
                  <p className="mt-1 font-mono text-xs">${alert.amount} · {alert.daysOverdue ?? 0}d overdue</p>
                ) : null}
                <div className="mt-3 flex gap-2">
                  <Button size="sm" variant="secondary" onClick={() => previewAdminAction("billing.send_reminder", alert.organization)}>
                    Send reminder
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => previewAdminAction("billing.apply_credit", alert.organization)}>
                    Apply credit
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </SectionPanel>
      </div>

      <SectionPanel title="Fleet actions">
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => previewAdminAction("billing.dunning_batch")}>
            Run dunning batch
          </Button>
          <Button variant="secondary" onClick={() => previewAdminAction("billing.reconcile_invoices")}>
            Reconcile invoices
          </Button>
          <Button variant="secondary" onClick={() => previewAdminAction("billing.export_ledger")}>
            Export ledger
          </Button>
        </div>
      </SectionPanel>
    </div>
  );
}
