"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ArrowLeft, Ban, CheckCircle2 } from "lucide-react";
import { OpsPageFrame } from "@/components/admin/ops-page-frame";
import { formatOpsDate, Mono, OpsStatus } from "@/components/admin/ops-primitives";
import { OpsTable, type OpsColumn } from "@/components/admin/ops-table";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { useAdminResource } from "@/hooks/use-admin-resource";
import {
  getOpsTenant,
  getOpsTenantApiKeys,
  getOpsTenantDomains,
  getOpsTenantEmails,
  getOpsTenantUsage,
  getOpsTenantUsers,
  getOpsTenantWebhooks,
  setOpsTenantAccess,
} from "@/services/admin-ops";
import type { OpsApiKey, OpsDomain, OpsEmail, OpsTenant, OpsTenantUsage, OpsUser, OpsWebhook } from "@/types/ops";

type TenantDetail = {
  tenant: OpsTenant;
  usage: OpsTenantUsage;
  emails: OpsEmail[];
  users: OpsUser[];
  domains: OpsDomain[];
  apiKeys: OpsApiKey[];
  webhooks: OpsWebhook[];
};

async function loadTenant(id: string): Promise<TenantDetail> {
  const [tenant, usage, emails, users, domains, apiKeys, webhooks] = await Promise.all([
    getOpsTenant(id),
    getOpsTenantUsage(id),
    getOpsTenantEmails(id),
    getOpsTenantUsers(id),
    getOpsTenantDomains(id),
    getOpsTenantApiKeys(id),
    getOpsTenantWebhooks(id),
  ]);
  return {
    tenant,
    usage,
    emails: emails.items,
    users: users.items,
    domains: domains.items,
    apiKeys: apiKeys.items,
    webhooks: webhooks.items,
  };
}

const emailColumns: OpsColumn<OpsEmail>[] = [
  { key: "created", label: "Created", value: (row) => row.createdAt, render: (row) => formatOpsDate(row.createdAt) },
  { key: "recipient", label: "Recipient", value: (row) => row.recipient },
  { key: "sender", label: "Sender", value: (row) => row.fromAddress },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "attempts", label: "Attempts", value: (row) => row.attemptCount ?? 0 },
  { key: "message", label: "Message ID", value: (row) => row.id, render: (row) => <Mono>{row.id}</Mono> },
];

const userColumns: OpsColumn<OpsUser>[] = [
  { key: "name", label: "User", value: (row) => row.displayName },
  { key: "email", label: "Email", value: (row) => row.email },
  { key: "role", label: "Role", value: (row) => row.role, render: (row) => <OpsStatus value={row.role} /> },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "created", label: "Created", value: (row) => row.createdAt, render: (row) => formatOpsDate(row.createdAt) },
];

const domainColumns: OpsColumn<OpsDomain>[] = [
  { key: "domain", label: "Domain", value: (row) => row.domain },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "verification", label: "Verification", value: (row) => row.verificationStatus, render: (row) => <OpsStatus value={row.verificationStatus} /> },
  { key: "mailFrom", label: "MAIL FROM", value: (row) => row.mailFromDomain },
  { key: "updated", label: "Updated", value: (row) => row.updatedAt, render: (row) => formatOpsDate(row.updatedAt) },
];

const apiKeyColumns: OpsColumn<OpsApiKey>[] = [
  { key: "name", label: "Key", value: (row) => row.name },
  { key: "prefix", label: "Prefix", value: (row) => row.keyPrefix, render: (row) => <Mono>{row.keyPrefix}</Mono> },
  { key: "environment", label: "Environment", value: (row) => row.environment, render: (row) => <OpsStatus value={row.environment} /> },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "lastUsed", label: "Last used", value: (row) => row.lastUsedAt, render: (row) => formatOpsDate(row.lastUsedAt) },
];

const webhookColumns: OpsColumn<OpsWebhook>[] = [
  { key: "created", label: "Created", value: (row) => row.createdAt, render: (row) => formatOpsDate(row.createdAt) },
  { key: "event", label: "Event", value: (row) => row.eventType },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "attempts", label: "Attempts", value: (row) => row.attemptCount },
  { key: "http", label: "HTTP", value: (row) => row.lastResponseCode ?? "-" },
  { key: "error", label: "Last error", value: (row) => row.lastError },
];

function Summary({ label, value }: { label: string; value: React.ReactNode }) {
  return <div className="min-w-0"><dt className="tech-label">{label}</dt><dd className="mt-1 break-words text-sm">{value || "-"}</dd></div>;
}

export function AdminTenantDetailScreen({ id }: { id: string }) {
  const router = useRouter();
  const resource = useAdminResource(() => loadTenant(id), [id]);
  const [confirmAccess, setConfirmAccess] = useState(false);
  const [updatingAccess, setUpdatingAccess] = useState(false);

  async function updateAccess() {
    const tenant = resource.data?.tenant;
    if (!tenant) return;
    const enabled = tenant.status !== "ACTIVE";
    setUpdatingAccess(true);
    try {
      await setOpsTenantAccess(tenant.id, enabled);
      toast.success(enabled ? "Tenant access restored" : "Tenant suspended");
      setConfirmAccess(false);
      resource.reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Unable to update tenant access");
    } finally {
      setUpdatingAccess(false);
    }
  }

  if (resource.loading) return <OpsPageFrame title="Tenant detail" description="Loading tenant records..."><LoadingState rows={8} /></OpsPageFrame>;
  if (resource.error || !resource.data) return <OpsPageFrame title="Tenant detail" description="Tenant operations and account access."><ErrorState description={resource.error ?? "Tenant was not found."} onRetry={resource.reload} /></OpsPageFrame>;

  const { tenant, usage, emails, users, domains, apiKeys, webhooks } = resource.data;
  const owner = users.find((user) => user.role === "OWNER");
  const enabled = tenant.status === "ACTIVE";

  return <OpsPageFrame title={tenant.name} description={`${tenant.slug} · ${owner?.email ?? "Owner not listed"}`} actions={<Button asChild size="sm" variant="secondary"><Link href="/admin/tenants"><ArrowLeft />All tenants</Link></Button>}>
    <SectionPanel ambient title="Tenant account" description="Workspace identity, access, billing, and current usage">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex flex-wrap items-center gap-2"><OpsStatus value={tenant.status} /><OpsStatus value={usage.subscriptionStatus} /><span className="rounded-md border px-2 py-1 text-xs">{usage.planName} · {usage.planCode}</span></div>
        <Button type="button" variant={enabled ? "destructive" : "primary"} onClick={() => setConfirmAccess(true)}>
          {enabled ? <Ban /> : <CheckCircle2 />}{enabled ? "Suspend tenant" : "Restore tenant access"}
        </Button>
      </div>
      <dl className="mt-5 grid gap-4 border-t border-border/70 pt-5 sm:grid-cols-2 xl:grid-cols-4">
        <Summary label="Tenant ID" value={<Mono>{tenant.id}</Mono>} />
        <Summary label="Owner" value={owner ? `${owner.displayName} · ${owner.email}` : "No owner user found"} />
        <Summary label="Created" value={formatOpsDate(tenant.createdAt)} />
        <Summary label="Last updated" value={formatOpsDate(tenant.updatedAt)} />
        <Summary label="Subscription period" value={`${formatOpsDate(usage.currentPeriodStart)} - ${formatOpsDate(usage.currentPeriodEnd)}`} />
        <Summary label="Subscription ID" value={<Mono>{usage.subscriptionId}</Mono>} />
      </dl>
      <div className="mt-5 grid gap-3 border-t border-border/70 pt-5 sm:grid-cols-2 xl:grid-cols-4">
        {usage.usage.metrics.map((metric) => <div key={metric.metric} className="rounded-lg border border-border/70 p-3"><p className="tech-label">{metric.metric.replaceAll("_", " ")}</p><p className="mt-1 font-mono text-lg tabular-nums">{metric.used.toLocaleString()} <span className="text-sm text-muted-foreground">/ {metric.limit == null ? "unlimited" : metric.limit.toLocaleString()}</span></p><p className="mt-1 text-xs text-muted-foreground">{metric.remaining == null ? "No usage limit" : `${metric.remaining.toLocaleString()} remaining`}</p></div>)}
      </div>
      <div className="mt-5 flex flex-wrap gap-2 border-t border-border/70 pt-5">
        {Object.entries(usage.entitlements.features).map(([feature, active]) => <span key={feature} className="inline-flex items-center gap-2 rounded-md border px-2 py-1 text-xs"><span className={`size-1.5 rounded-full ${active ? "bg-success" : "bg-muted-foreground/40"}`} />{feature.replaceAll("_", " ")}</span>)}
      </div>
    </SectionPanel>

    <SectionPanel title={`Email activity · ${emails.length}`} description="Latest tenant messages; open a row for delivery attempts and feedback." className="min-w-0 overflow-hidden p-0">
      <OpsTable rows={emails} columns={emailColumns} rowKey={(row) => row.id} onRowClick={(row) => router.push(`/admin/emails/${encodeURIComponent(row.id)}`)} emptyTitle="No email messages for this tenant" />
    </SectionPanel>

    <SectionPanel title={`Users · ${users.length}`} description="Tenant identities and roles; password hashes and session tokens are never exposed." className="min-w-0 overflow-hidden p-0">
      <OpsTable rows={users} columns={userColumns} rowKey={(row) => row.id} emptyTitle="No tenant users found" />
    </SectionPanel>

    <div className="grid gap-4 xl:grid-cols-2">
      <SectionPanel title={`Domains · ${domains.length}`} description="Sender verification and DNS status." className="min-w-0 overflow-hidden p-0"><OpsTable rows={domains} columns={domainColumns} rowKey={(row) => row.id} emptyTitle="No sending domains" /></SectionPanel>
      <SectionPanel title={`API keys · ${apiKeys.length}`} description="Key metadata only; secret values are not available to admins." className="min-w-0 overflow-hidden p-0"><OpsTable rows={apiKeys} columns={apiKeyColumns} rowKey={(row) => row.id} emptyTitle="No API keys" /></SectionPanel>
    </div>

    <SectionPanel title={`Webhook deliveries · ${webhooks.length}`} description="Latest tenant webhook delivery and retry history." className="min-w-0 overflow-hidden p-0"><OpsTable rows={webhooks} columns={webhookColumns} rowKey={(row) => row.id} emptyTitle="No webhook deliveries" /></SectionPanel>

    {!enabled ? <Alert variant="error"><AlertDescription>This tenant is suspended. Dashboard sessions and API keys are rejected until access is restored.</AlertDescription></Alert> : null}

    <Dialog open={confirmAccess} onOpenChange={setConfirmAccess}>
      <DialogContent>
        <DialogHeader><DialogTitle>{enabled ? "Suspend this tenant?" : "Restore tenant access?"}</DialogTitle><DialogDescription>{enabled ? "All tenant dashboard sessions and API keys will stop authenticating. Existing email and billing records remain available to operators." : "This tenant will be able to sign in and authenticate active API keys again."}</DialogDescription></DialogHeader>
        <DialogFooter><Button type="button" variant="secondary" onClick={() => setConfirmAccess(false)}>Cancel</Button><Button type="button" variant={enabled ? "destructive" : "primary"} onClick={updateAccess} loading={updatingAccess}>{enabled ? "Suspend tenant" : "Restore access"}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </OpsPageFrame>;
}