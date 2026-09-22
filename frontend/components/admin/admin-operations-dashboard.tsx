"use client";

import Link from "next/link";
import { Activity, AlertTriangle, ArrowRight, MailCheck, RefreshCw, Server, ShieldCheck } from "lucide-react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { OpsPageFrame } from "@/components/admin/ops-page-frame";
import { Mono, OpsStatus, formatOpsDate, shortId } from "@/components/admin/ops-primitives";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { Progress } from "@/components/ui/progress";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { getOpsDashboard, getOpsHealth, getOpsPage, getOpsQueues } from "@/services/admin-ops";
import type { OpsAudit, OpsEmail, OpsTraffic } from "@/types/ops";

function pct(value: number) { return `${(value * 100).toFixed(2)}%`; }
function Kpi({ label, value, detail, icon: Icon }: { label: string; value: string; detail: string; icon: typeof Activity }) {
  return <SectionPanel className="p-4 sm:p-5"><div className="flex items-start justify-between gap-3"><div><p className="tech-label">{label}</p><p className="mt-2 text-2xl font-semibold tracking-tight">{value}</p><p className="mt-1 text-xs text-muted-foreground">{detail}</p></div><div className="rounded-xl bg-primary/10 p-2.5 text-primary"><Icon className="size-4" /></div></div></SectionPanel>;
}

export function AdminOperationsDashboard() {
  const resource = useAdminResource(async () => {
    const [dashboard, queues, health, traffic, audit, failures] = await Promise.all([
      getOpsDashboard(), getOpsQueues(), getOpsHealth(), getOpsPage<OpsTraffic>("/api/v1/admin/http-request-audit?page=0&size=6"),
      getOpsPage<OpsAudit>("/api/v1/admin/audit?page=0&size=6"), getOpsPage<OpsEmail>("/api/v1/admin/emails?status=FAILED&page=0&size=6"),
    ]);
    return { dashboard, queues, health, traffic, audit, failures };
  }, []);
  if (resource.loading) return <OpsPageFrame title="Operations console" description="Loading platform telemetry…"><LoadingState rows={9} /></OpsPageFrame>;
  if (resource.error || !resource.data) return <OpsPageFrame title="Operations console" description="Delivery, traffic, queue, and system health."><ErrorState description={resource.error} onRetry={resource.reload} /></OpsPageFrame>;
  const { dashboard, queues, health, traffic, audit, failures } = resource.data;
  const statusData = [
    ["Queued", dashboard.queued], ["Processing", dashboard.processing], ["Sending", dashboard.sending], ["Delivered", dashboard.delivered],
    ["Deferred", dashboard.deferred], ["Failed", dashboard.failed], ["Bounced", dashboard.bounced], ["Suppressed", dashboard.suppressed],
  ] as const;
  const trend = Array.from({ length: 7 }, (_, offset) => {
    const date = new Date(); date.setDate(date.getDate() - (6 - offset));
    const key = date.toISOString().slice(0, 10);
    const activities = dashboard.recentActivity.filter((item) => item.at.startsWith(key));
    return { date: date.toLocaleDateString("en", { weekday: "short" }), delivered: activities.filter((item) => item.status === "DELIVERED").length, bounced: activities.filter((item) => item.status === "BOUNCED").length, complaints: activities.filter((item) => item.type.toLowerCase().includes("complaint")).length };
  });
  return <OpsPageFrame title="Operations console" description="Live delivery posture, platform health, queues, and recent operator activity." actions={<Button variant="secondary" size="sm" onClick={resource.reload}><RefreshCw className="size-3.5" />Refresh</Button>}>
    <div className="grid grid-cols-2 gap-3 xl:grid-cols-4"><Kpi label="Messages" value={dashboard.totalMessages.toLocaleString()} detail={`${dashboard.queued.toLocaleString()} currently queued`} icon={Activity} /><Kpi label="Delivery rate" value={pct(dashboard.deliveryRate)} detail={`${dashboard.delivered.toLocaleString()} delivered`} icon={MailCheck} /><Kpi label="Bounce rate" value={pct(dashboard.bounceRate)} detail={`${dashboard.bounced.toLocaleString()} bounce events`} icon={AlertTriangle} /><Kpi label="Complaint rate" value={pct(dashboard.complaintRate)} detail={`${dashboard.complaints.toLocaleString()} complaints`} icon={ShieldCheck} /></div>
    <div className="grid gap-4 xl:grid-cols-[.8fr_1.2fr]">
      <SectionPanel title="Delivery funnel" description="Share of all recorded messages"><div className="space-y-4">{[["Accepted", dashboard.totalMessages],["Attempted", Math.max(0, dashboard.totalMessages - dashboard.queued)],["Delivered", dashboard.delivered]] .map(([label, value]) => { const number = Number(value); return <div key={String(label)}><div className="mb-1.5 flex justify-between text-xs"><span>{label}</span><Mono>{number.toLocaleString()}</Mono></div><Progress value={dashboard.totalMessages ? (number/dashboard.totalMessages)*100 : 0} /></div>; })}</div></SectionPanel>
      <SectionPanel title="Messages by status" description="Current lifecycle distribution"><div className="grid grid-cols-2 gap-2 sm:grid-cols-4">{statusData.map(([label,value]) => <div key={label} className="rounded-xl border border-border/70 bg-card/50 p-3"><OpsStatus value={label} /><p className="mt-3 font-mono text-lg font-semibold">{value.toLocaleString()}</p></div>)}</div></SectionPanel>
    </div>
    <SectionPanel title="Delivery, bounce & complaint trends" description="Seven-day view from the recent activity window supplied by the operations API" className="min-w-0 overflow-hidden"><div className="h-64"><ResponsiveContainer width="100%" height="100%"><AreaChart data={trend} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}><CartesianGrid stroke="var(--border)" vertical={false}/><XAxis dataKey="date" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} axisLine={false}/><YAxis tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} axisLine={false}/><Tooltip contentStyle={{ background: "var(--popover)", border: "1px solid var(--border)", borderRadius: 10 }}/><Area dataKey="delivered" stroke="var(--chart-1)" fill="var(--primary-soft)" strokeWidth={2}/><Area dataKey="bounced" stroke="var(--chart-4)" fill="none" strokeWidth={2}/><Area dataKey="complaints" stroke="var(--chart-5)" fill="none" strokeWidth={2}/></AreaChart></ResponsiveContainer></div></SectionPanel>
    <div className="grid gap-4 xl:grid-cols-2">
      <SectionPanel title="Recent failures" action={<Button asChild variant="ghost" size="sm"><Link href="/admin/emails?status=FAILED">Email explorer<ArrowRight /></Link></Button>}><div className="space-y-2">{failures.items.map((item) => <Link key={item.id} href={`/admin/emails/${item.id}`} className="block rounded-xl border border-border/70 p-3 hover:border-primary/30"><div className="flex items-center justify-between gap-2"><span className="truncate text-sm font-medium">{item.recipient}</span><OpsStatus value={item.status}/></div><p className="mt-1 truncate text-xs text-muted-foreground">{item.lastError ?? item.statusReason ?? "No error detail"}</p><p className="mt-1 font-mono text-[10px] text-muted-foreground">{formatOpsDate(item.failedAt ?? item.createdAt)}</p></Link>)}</div></SectionPanel>
      <SectionPanel title="Queue status" description="Secure passive RabbitMQ queue inspection" action={<Button asChild variant="ghost" size="sm"><Link href="/admin/queues">Details<ArrowRight /></Link></Button>}><div className="space-y-2">{queues.queues.map((queue) => <div key={queue.name} className="flex items-center justify-between gap-3 rounded-xl border border-border/70 p-3"><div><p className="font-mono text-xs">{queue.name}</p><p className="mt-1 text-[11px] text-muted-foreground">{queue.consumers} consumers</p></div><div className="text-right"><OpsStatus value={queue.available ? "AVAILABLE" : "DOWN"}/><p className="mt-1 font-mono text-sm">{queue.messages.toLocaleString()} msgs</p></div></div>)}</div></SectionPanel>
    </div>
    <SectionPanel title="System health" description="Read-only dependency probes" action={<Button asChild variant="ghost" size="sm"><Link href="/admin/system">System detail<ArrowRight /></Link></Button>}><div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">{Object.entries(health.components).map(([name,status]) => <div key={name} className="rounded-xl border border-border/70 p-3"><div className="flex items-center justify-between"><Server className="size-4 text-muted-foreground"/><OpsStatus value={status}/></div><p className="mt-3 text-sm font-medium capitalize">{name}</p></div>)}</div></SectionPanel>
    <div className="grid gap-4 xl:grid-cols-2">
      <SectionPanel title="Recent traffic" action={<Button asChild variant="ghost" size="sm"><Link href="/admin/traffic">All traffic<ArrowRight /></Link></Button>}><div className="space-y-2">{traffic.items.map((item) => <div key={item.id} className="grid grid-cols-[auto_1fr_auto] items-center gap-2 border-b border-border/50 pb-2 last:border-0"><OpsStatus value={item.httpMethod}/><span className="truncate font-mono text-xs">{item.route}</span><div className="text-right"><OpsStatus value={item.statusCode}/><p className="mt-1 font-mono text-[10px] text-muted-foreground">{item.durationMs} ms</p></div></div>)}</div></SectionPanel>
      <SectionPanel title="Recent audit events" action={<Button asChild variant="ghost" size="sm"><Link href="/admin/audit">Full audit<ArrowRight /></Link></Button>}><div className="space-y-2">{audit.items.map((item) => <div key={item.id} className="border-b border-border/50 pb-2 last:border-0"><div className="flex items-center justify-between gap-2"><span className="truncate text-sm"><strong>{item.method}</strong> {item.path}</span><OpsStatus value={item.statusCode}/></div><div className="mt-1 flex justify-between gap-2 text-[10px] text-muted-foreground"><Mono>{shortId(item.requestId)}</Mono><span>{formatOpsDate(item.createdAt)}</span></div></div>)}</div></SectionPanel>
    </div>
  </OpsPageFrame>;
}
