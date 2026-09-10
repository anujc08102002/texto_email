"use client";

import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { ADMIN_AUDIT } from "@/lib/presentation/admin";
import { formatDateTime } from "@/lib/format";

export function AdminAuditScreen() {
  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Platform admin"
        title="Audit log"
        description="Immutable record of operator actions across tenants."
      />
      <AdminPreviewBanner />

      <SectionPanel title="Recent events">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b border-border/70 text-[10px] font-semibold tracking-[0.12em] text-muted-foreground uppercase">
                <th className="pb-2">Timestamp</th>
                <th className="pb-2">Actor</th>
                <th className="pb-2">Action</th>
                <th className="pb-2">Tenant</th>
                <th className="pb-2">Detail</th>
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
