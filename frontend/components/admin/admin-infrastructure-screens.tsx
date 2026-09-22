"use client";

import { CheckCircle2, Clock3, Database, MessageSquareMore, RefreshCw, Server, TriangleAlert } from "lucide-react";
import { OpsPageFrame } from "@/components/admin/ops-page-frame";
import { Mono, OpsStatus, formatOpsDate } from "@/components/admin/ops-primitives";
import { OpsTable, type OpsColumn } from "@/components/admin/ops-table";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { getOpsHealth, getOpsPage, getOpsQueues, getPublicPlatformStatus } from "@/services/admin-ops";
import type { OpsOutbox } from "@/types/ops";

const outboxColumns: OpsColumn<OpsOutbox>[] = [
  { key: "created", label: "Created", value: (row) => row.createdAt, render: (row) => formatOpsDate(row.createdAt) },
  { key: "event", label: "Event", value: (row) => row.eventType }, { key: "aggregate", label: "Aggregate", value: (row) => row.aggregateType },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "attempts", label: "Attempts", value: (row) => row.attempts }, { key: "error", label: "Last error", value: (row) => row.lastError },
];

export function AdminQueuesScreen() {
  const resource = useAdminResource(async () => { const [queues, outbox] = await Promise.all([getOpsQueues(), getOpsPage<OpsOutbox>("/api/v1/admin/outbox?page=0&size=20")]); return { queues, outbox }; }, []);
  if (resource.loading) return <OpsPageFrame title="Queues" description="Loading broker and outbox state…"><LoadingState rows={7}/></OpsPageFrame>;
  if (resource.error || !resource.data) return <OpsPageFrame title="Queues" description="RabbitMQ, SES feedback, outbox, and DLQ posture."><ErrorState description={resource.error} onRetry={resource.reload}/></OpsPageFrame>;
  return <OpsPageFrame title="Queues" description="Secure, read-only view of outbox publication and RabbitMQ delivery infrastructure." actions={<Button variant="secondary" size="sm" onClick={resource.reload}><RefreshCw/>Refresh</Button>}>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{resource.data.queues.queues.map((queue) => { const dlq = queue.name.toLowerCase().includes("dlq") || queue.name.toLowerCase().includes("dead"); const feedback = queue.name.toLowerCase().includes("feedback") || queue.name.toLowerCase().includes("ses"); return <SectionPanel key={queue.name} className="p-4"><div className="flex items-center justify-between"><div className="rounded-xl bg-primary/10 p-2 text-primary">{dlq ? <TriangleAlert className="size-4"/> : feedback ? <MessageSquareMore className="size-4"/> : <CheckCircle2 className="size-4"/>}</div><OpsStatus value={queue.available ? "AVAILABLE" : "DOWN"}/></div><p className="mt-4 truncate font-mono text-xs" title={queue.name}>{queue.name}</p><div className="mt-3 flex justify-between text-xs"><span>{queue.messages.toLocaleString()} messages</span><span className="text-muted-foreground">{queue.consumers} consumers</span></div></SectionPanel>; })}</div>
    <SectionPanel title="Outbox" description={`${resource.data.outbox.totalElements.toLocaleString()} events · newest first`} className="min-w-0 overflow-hidden p-0"><OpsTable rows={resource.data.outbox.items} columns={outboxColumns} rowKey={(row) => row.id} emptyTitle="Outbox is empty"/></SectionPanel>
  </OpsPageFrame>;
}

export function AdminSystemScreen() {
  const resource = useAdminResource(async () => { const [health, platform, queues] = await Promise.all([getOpsHealth(), getPublicPlatformStatus(), getOpsQueues()]); return { health, platform, queues }; }, []);
  if (resource.loading) return <OpsPageFrame title="System" description="Running dependency probes…"><LoadingState rows={6}/></OpsPageFrame>;
  if (resource.error || !resource.data) return <OpsPageFrame title="System" description="API and dependency health."><ErrorState description={resource.error} onRetry={resource.reload}/></OpsPageFrame>;
  const { health, platform, queues } = resource.data;
  const rabbit = health.components.rabbitmq ?? "UNKNOWN";
  const mta = health.components.ses ?? health.components.mta ?? "UNKNOWN";
  const feedbackQueue = queues.queues.find((queue) => /ses|feedback/i.test(queue.name));
  const cards = [
    { name: "API health", status: health.status, detail: "Authenticated operations API" },
    { name: "PostgreSQL", status: health.components.database ?? "UNKNOWN", detail: "Read-only connectivity probe" },
    { name: "Redis", status: health.components.redis ?? "UNKNOWN", detail: "PING connectivity probe" },
    { name: "RabbitMQ", status: rabbit, detail: "Passive queue declaration probe" },
    { name: "SES / MTA", status: mta, detail: "Configured mail transport probe" },
    { name: "Feedback consumer", status: feedbackQueue ? (feedbackQueue.available && feedbackQueue.consumers > 0 ? "UP" : "DEGRADED") : "NOT REPORTED", detail: feedbackQueue ? `${feedbackQueue.consumers} active consumers` : "No feedback queue telemetry returned" },
  ];
  return <OpsPageFrame title="System" description="Live dependency probes and release metadata. No credentials or connection strings are exposed." actions={<Button variant="secondary" size="sm" onClick={resource.reload}><RefreshCw/>Refresh probes</Button>}>
    <SectionPanel ambient><div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"><div className="flex items-center gap-3"><div className="rounded-2xl bg-primary/10 p-3 text-primary"><Server className="size-5"/></div><div><p className="tech-label">Overall status</p><div className="mt-1"><OpsStatus value={health.status}/></div></div></div><div className="grid grid-cols-2 gap-6 text-sm"><div><p className="tech-label">Application version</p><Mono className="mt-1 block">{platform?.version ?? "Not reported"}</Mono></div><div><p className="tech-label">Uptime</p><span className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground"><Clock3 className="size-3.5"/>Not reported by API</span></div></div></div></SectionPanel>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{cards.map((item) => <SectionPanel key={item.name} className="p-4 sm:p-5"><div className="flex items-start justify-between gap-2"><div className="rounded-xl bg-muted p-2 text-muted-foreground">{item.name === "PostgreSQL" ? <Database className="size-4"/> : <Server className="size-4"/>}</div><OpsStatus value={item.status}/></div><h2 className="mt-4 text-sm font-semibold">{item.name}</h2><p className="mt-1 text-xs text-muted-foreground">{item.detail}</p></SectionPanel>)}</div>
    <SectionPanel title="Probe semantics" description="Health checks are intentionally narrow and non-destructive"><p className="text-sm leading-6 text-muted-foreground">The console reports only the states returned by secured backend probes. “Not reported” is distinct from “down” and prevents the UI from claiming health for components it cannot verify.</p></SectionPanel>
  </OpsPageFrame>;
}
