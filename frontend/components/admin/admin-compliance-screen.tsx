"use client";

import Link from "next/link";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { previewAdminAction } from "@/lib/admin/actions";
import { ADMIN_COMPLIANCE } from "@/lib/presentation/admin";
import { formatDateTime } from "@/lib/format";

export function AdminComplianceScreen() {
  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Platform admin"
        title="Compliance"
        description="Abuse reports, spam complaints, and manual review queue."
      />
      <AdminPreviewBanner />

      <SectionPanel title="Open cases">
        <div className="space-y-3">
          {ADMIN_COMPLIANCE.map((item) => (
            <div key={item.id} className="rounded-xl border border-border/70 px-4 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={item.severity === "high" ? "error" : "warning"}>{item.severity}</Badge>
                <Badge variant="outline">{item.type.replaceAll("_", " ")}</Badge>
                <Badge variant="secondary">{item.status}</Badge>
              </div>
              <p className="mt-2 text-sm font-medium">{item.organization}</p>
              <p className="mt-1 text-sm text-muted-foreground">{item.summary}</p>
              <p className="mt-2 font-mono text-[11px] text-muted-foreground">{formatDateTime(item.createdAt)}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button size="sm" variant="secondary" onClick={() => previewAdminAction("compliance.investigate", item.organization)}>
                  Mark investigating
                </Button>
                <Button size="sm" variant="secondary" onClick={() => previewAdminAction("sending.freeze", item.organization)}>
                  Freeze sending
                </Button>
                <Button size="sm" variant="secondary" onClick={() => previewAdminAction("tenant.suspend", item.organization)}>
                  Suspend
                </Button>
                <Button asChild size="sm" variant="ghost">
                  <Link href={`/admin/tenants/${item.tenantId}`}>Open tenant</Link>
                </Button>
              </div>
            </div>
          ))}
        </div>
      </SectionPanel>
    </div>
  );
}
