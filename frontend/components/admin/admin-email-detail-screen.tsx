"use client";

import Link from "next/link";
import { ArrowLeft, Copy, Mail, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { OpsPageFrame } from "@/components/admin/ops-page-frame";
import { OpsStatus, formatOpsDate } from "@/components/admin/ops-primitives";
import { OpsTable, type OpsColumn } from "@/components/admin/ops-table";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { getOpsEmail } from "@/services/admin-ops";
import type { OpsAttempt } from "@/types/ops";

const attemptColumns: OpsColumn<OpsAttempt>[] = [
  { key: "attempt", label: "Attempt", value: (row) => row.attemptNumber },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "started", label: "Started", value: (row) => row.startedAt, render: (row) => formatOpsDate(row.startedAt) },
  { key: "completed", label: "Completed", value: (row) => row.completedAt, render: (row) => formatOpsDate(row.completedAt) },
  { key: "provider", label: "SES / provider response", value: (row) => row.providerResponse, render: (row) => <span className="block max-w-md whitespace-pre-wrap text-xs">{row.providerResponse ?? "—"}</span> },
  { key: "error", label: "Error", value: (row) => row.errorMessage, render: (row) => <span className="text-xs text-destructive">{row.errorMessage ?? "—"}</span> },
];

function Field({ label, value, mono = false }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return <div className="min-w-0"><dt className="tech-label">{label}</dt><dd className={`mt-1 break-words text-sm ${mono ? "font-mono text-xs" : ""}`}>{value || "—"}</dd></div>;
}

export function AdminEmailDetailScreen({ id }: { id: string }) {
  const resource = useAdminResource(() => getOpsEmail(id), [id]);
  async function copy(value: string) {
    await navigator.clipboard.writeText(value);
    toast.success("Identifier copied");
  }
  if (resource.loading) return <OpsPageFrame title="Email detail" description="Loading message lifecycle…"><LoadingState rows={8} /></OpsPageFrame>;
  if (resource.error || !resource.data) return <OpsPageFrame title="Email detail" description="Message lifecycle and provider telemetry."><ErrorState description={resource.error ?? "Message was not found."} onRetry={resource.reload} /></OpsPageFrame>;
  const { message, timeline, deliveryAttempts, bounces, complaints, suppressions, timestamps } = resource.data;
  return <OpsPageFrame title="Email detail" description="Complete operational lifecycle without exposing message content." actions={<Button asChild variant="secondary" size="sm"><Link href="/admin/emails"><ArrowLeft />All emails</Link></Button>}>
    <SectionPanel ambient>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"><div><div className="flex flex-wrap items-center gap-2"><OpsStatus value={message.status} /><span className="text-xs text-muted-foreground">{message.statusReason}</span></div><p className="mt-3 text-lg font-semibold">{message.fromAddress} <span className="font-normal text-muted-foreground">→</span> {message.recipient}</p></div><Button type="button" variant="ghost" size="sm" onClick={() => copy(message.id)}><Copy className="size-3.5" />Copy message ID</Button></div>
      <dl className="mt-5 grid gap-4 border-t border-border/70 pt-5 sm:grid-cols-2 xl:grid-cols-4"><Field label="Message ID" value={message.id} mono /><Field label="Provider message ID" value={message.providerMessageId} mono /><Field label="RFC 822 ID" value={message.rfc822MessageId} mono /><Field label="Tenant ID" value={message.tenantId} mono /><Field label="Queued" value={formatOpsDate(message.queuedAt)} /><Field label="Delivered" value={formatOpsDate(message.deliveredAt)} /><Field label="Failed" value={formatOpsDate(message.failedAt)} /><Field label="Attempts" value={`${message.attemptCount ?? 0} / ${message.maxAttempts ?? "—"}`} /><Field label="Request / trace IDs" value="Not reported by email detail API" mono /></dl>
    </SectionPanel>

    <div className="grid gap-4 xl:grid-cols-[minmax(18rem,.72fr)_minmax(0,1.28fr)]">
      <SectionPanel title="Event timeline" description="Chronological state transitions and feedback">
        {timeline.length ? <ol className="relative ml-2 border-l border-border pl-5">{timeline.map((event, index) => <li key={`${event.at}-${index}`} className="relative pb-6 last:pb-0"><span className="absolute -left-[1.63rem] top-1 size-3 rounded-full border-2 border-card bg-primary" /><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-medium">{event.type.replaceAll("_", " ")}</p><OpsStatus value={event.status} /></div><p className="mt-1 text-xs text-muted-foreground">{event.detail}</p><time className="mt-1 block font-mono text-[10px] text-muted-foreground">{formatOpsDate(event.at)}</time></li>)}</ol> : <EmptyState icon={<Mail />} title="No timeline events" className="border-0" />}
      </SectionPanel>
      <SectionPanel title="Delivery attempts" description="Provider responses are shown as returned by the secured ops API" className="min-w-0 overflow-hidden p-0"><OpsTable rows={deliveryAttempts} columns={attemptColumns} rowKey={(row) => row.id} emptyTitle="No delivery attempts" /></SectionPanel>
    </div>

    <div className="grid gap-4 lg:grid-cols-3">
      <SectionPanel title={`Bounces · ${bounces.length}`} description="Classification and diagnostics">{bounces.length ? <div className="space-y-3">{bounces.map((item) => <div key={item.id} className="rounded-xl border p-3"><div className="flex gap-2"><OpsStatus value={item.bounceClass} /><OpsStatus value={item.correlationStatus} /></div><p className="mt-2 text-sm">{item.finalRecipient}</p><p className="mt-1 text-xs text-muted-foreground">{item.statusCode} · {item.diagnosticMessage}</p></div>)}</div> : <p className="text-sm text-muted-foreground">No bounce feedback.</p>}</SectionPanel>
      <SectionPanel title={`Complaints · ${complaints.length}`} description="Provider abuse feedback">{complaints.length ? <div className="space-y-3">{complaints.map((item) => <div key={item.id} className="rounded-xl border p-3"><div className="flex gap-2"><ShieldAlert className="size-4 text-destructive" /><OpsStatus value={item.processingStatus} /></div><p className="mt-2 text-sm">{item.recipient}</p><p className="mt-1 text-xs text-muted-foreground">{item.complaintType} · {item.diagnostic}</p></div>)}</div> : <p className="text-sm text-muted-foreground">No complaint feedback.</p>}</SectionPanel>
      <SectionPanel title={`Suppressions · ${suppressions.length}`} description="Current recipient policy state">{suppressions.length ? <div className="space-y-3">{suppressions.map((item) => <div key={item.id} className="rounded-xl border p-3"><OpsStatus value={item.type} /><p className="mt-2 text-sm">{item.email}</p><p className="mt-1 text-xs text-muted-foreground">{item.source} · {item.reason}</p></div>)}</div> : <p className="text-sm text-muted-foreground">No suppression records.</p>}</SectionPanel>
    </div>
    <SectionPanel title="Timestamp ledger" description="All backend-provided lifecycle timestamps"><dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{Object.entries(timestamps).map(([key, value]) => <Field key={key} label={key.replaceAll("_", " ")} value={formatOpsDate(value)} mono />)}</dl></SectionPanel>
  </OpsPageFrame>;
}
