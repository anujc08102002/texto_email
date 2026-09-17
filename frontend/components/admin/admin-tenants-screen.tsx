"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { TenantStatusBadge } from "@/components/admin/tenant-status-badge";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ADMIN_TENANTS } from "@/lib/presentation/admin";
import type { AdminTenantStatus } from "@/types/admin";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";

const FILTERS: Array<{ id: "all" | AdminTenantStatus; label: string }> = [
  { id: "all", label: "All" },
  { id: "active", label: "Active" },
  { id: "past_due", label: "Past due" },
  { id: "suspended", label: "Suspended" },
  { id: "sending_frozen", label: "Frozen" },
  { id: "trial", label: "Trial" },
];

export function AdminTenantsScreen() {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<(typeof FILTERS)[number]["id"]>("all");

  const tenants = useMemo(() => {
    return ADMIN_TENANTS.filter((tenant) => {
      const matchesFilter = filter === "all" || tenant.status === filter;
      const q = query.trim().toLowerCase();
      const matchesQuery =
        !q ||
        tenant.organization.toLowerCase().includes(q) ||
        tenant.ownerEmail.toLowerCase().includes(q) ||
        tenant.slug.toLowerCase().includes(q) ||
        tenant.id.toLowerCase().includes(q);
      return matchesFilter && matchesQuery;
    });
  }, [filter, query]);

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Platform admin"
        title="Tenants"
        description="Search, filter, and open any workspace for full operator controls."
      />
      <AdminPreviewBanner />

      <SectionPanel className="flex min-h-0 flex-1 flex-col">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search organization, email, slug, or tenant id"
            className="w-full min-w-0 lg:max-w-md"
          />
          <div className="flex flex-wrap gap-1 rounded-lg border border-border/70 bg-muted/40 p-0.5">
            {FILTERS.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setFilter(item.id)}
                className={cn(
                  "rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors",
                  filter === item.id ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-4 hidden overflow-x-auto md:block">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead>
              <tr className="border-b border-border/70 text-[10px] font-semibold tracking-[0.12em] text-muted-foreground uppercase">
                <th className="pb-2">Organization</th>
                <th className="pb-2">Status</th>
                <th className="pb-2">Plan</th>
                <th className="pb-2">Usage</th>
                <th className="pb-2">Risk</th>
                <th className="pb-2">Last active</th>
                <th className="pb-2" />
              </tr>
            </thead>
            <tbody>
              {tenants.map((tenant) => (
                <tr key={tenant.id} className="border-b border-border/50 last:border-0">
                  <td className="py-3">
                    <p className="font-medium">{tenant.organization}</p>
                    <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">{tenant.ownerEmail}</p>
                  </td>
                  <td className="py-3">
                    <TenantStatusBadge status={tenant.status} />
                  </td>
                  <td className="py-3">
                    <p>{tenant.planName}</p>
                    <p className="font-mono text-[11px] text-muted-foreground">{tenant.planCode}</p>
                  </td>
                  <td className="py-3 font-mono text-xs tabular-nums">
                    {tenant.usagePct}% · {tenant.messages30d.toLocaleString()}
                  </td>
                  <td className="py-3">
                    <Badge variant={tenant.risk === "high" ? "error" : tenant.risk === "medium" ? "warning" : "success"}>
                      {tenant.risk}
                    </Badge>
                  </td>
                  <td className="py-3 font-mono text-xs text-muted-foreground">{formatDateTime(tenant.lastActiveAt)}</td>
                  <td className="py-3 text-right">
                    <Button asChild size="sm" variant="secondary">
                      <Link href={`/admin/tenants/${tenant.id}`}>Open</Link>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-4 space-y-2 md:hidden">
          {tenants.map((tenant) => (
            <Link
              key={tenant.id}
              href={`/admin/tenants/${tenant.id}`}
              className="block rounded-xl border border-border/70 bg-card/60 p-3"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate font-medium">{tenant.organization}</p>
                  <p className="mt-1 truncate font-mono text-[11px] text-muted-foreground">{tenant.ownerEmail}</p>
                </div>
                <TenantStatusBadge status={tenant.status} />
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <span className="text-xs text-muted-foreground">{tenant.planName}</span>
                <span className="font-mono text-[11px] text-muted-foreground">{tenant.usagePct}%</span>
                <Badge variant={tenant.risk === "high" ? "error" : tenant.risk === "medium" ? "warning" : "success"}>
                  {tenant.risk}
                </Badge>
              </div>
            </Link>
          ))}
        </div>

        {tenants.length === 0 ? (
          <p className="mt-6 text-center text-sm text-muted-foreground">No tenants match this filter.</p>
        ) : null}
      </SectionPanel>
    </div>
  );
}
