"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Database, Filter, RefreshCw, Search } from "lucide-react";
import { OpsPageFrame } from "@/components/admin/ops-page-frame";
import { Mono, OpsStatus, formatOpsDate, shortId } from "@/components/admin/ops-primitives";
import { OpsTable, type OpsColumn } from "@/components/admin/ops-table";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { LoadingState } from "@/components/ui/loading-state";
import { Pagination } from "@/components/ui/pagination";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { getOpsPage, queryString } from "@/services/admin-ops";

type Row = Record<string, unknown>;
type CatalogKind = "traffic" | "audit" | "domains" | "tenants" | "suppressions" | "bounces" | "complaints" | "webhooks" | "delivery-attempts" | "outbox";

const CONFIG: Record<CatalogKind, { title: string; description: string; endpoint: string; search: string; statusKey?: string; columns: Array<[string, string, "status" | "date" | "id" | "duration" | "dns" | "text"]> }> = {
  traffic: { title: "Traffic", description: "Sanitized HTTP request telemetry across the platform.", endpoint: "/api/v1/admin/http-request-audit", search: "Search route, request ID, tenant…", statusKey: "statusCode", columns: [["timestamp","Timestamp","date"],["httpMethod","Method","status"],["route","Route","text"],["statusCode","Status","status"],["durationMs","Duration","duration"],["tenantId","Tenant","id"],["requestId","Request ID","id"]] },
  audit: { title: "Audit log", description: "Immutable record of authenticated platform-admin actions.", endpoint: "/api/v1/admin/audit", search: "Search actor, action, target, correlation ID…", columns: [["createdAt","Timestamp","date"],["adminId","Actor","id"],["method","Action","status"],["path","Target","text"],["statusCode","Result","status"],["requestId","Correlation ID","id"]] },
  domains: { title: "Domains", description: "Verification and DNS authentication state across tenants.", endpoint: "/api/v1/admin/domains", search: "Search domain or tenant…", statusKey: "verificationStatus", columns: [["domain","Domain","text"],["verificationStatus","Verification","status"],["spf","SPF","dns"],["dkim","DKIM","dns"],["dmarc","DMARC","dns"],["mailFromDomain","MAIL FROM","text"],["tenantId","Tenant","id"]] },
  tenants: { title: "Tenants", description: "Create customer workspaces and manage their lifecycle status.", endpoint: "/api/v1/admin/tenants", search: "Search name, slug, or tenant ID…", statusKey: "status", columns: [["name","Tenant","text"],["slug","Slug","text"],["status","Status","status"],["id","Tenant ID","id"],["createdAt","Created","date"],["updatedAt","Updated","date"]] },
  suppressions: { title: "Suppressions", description: "Addresses blocked by bounce, complaint, unsubscribe, or policy.", endpoint: "/api/v1/admin/suppressions", search: "Search email, source, reason…", statusKey: "type", columns: [["email","Address","text"],["type","Type","status"],["source","Source","text"],["reason","Reason","text"],["tenantId","Tenant","id"],["messageId","Message","id"],["createdAt","Created","date"]] },
  bounces: { title: "Bounce events", description: "Provider feedback, classification, and message correlation.", endpoint: "/api/v1/admin/bounces", search: "Search recipient, diagnostic, event ID…", statusKey: "bounceClass", columns: [["receivedAt","Received","date"],["finalRecipient","Recipient","text"],["bounceClass","Class","status"],["failureKind","Failure","text"],["statusCode","SMTP","status"],["correlationStatus","Correlation","status"],["emailMessageId","Message","id"]] },
  complaints: { title: "Complaint events", description: "Abuse feedback, processing state, and provider correlation.", endpoint: "/api/v1/admin/complaints", search: "Search recipient, provider ID, diagnostic…", statusKey: "processingStatus", columns: [["receivedAt","Received","date"],["recipient","Recipient","text"],["complaintType","Type","status"],["processingStatus","Processing","status"],["provider","Provider","text"],["correlationStatus","Correlation","status"],["emailMessageId","Message","id"]] },
  webhooks: { title: "Webhook events", description: "Delivery history and retry state without exposing payloads or secrets.", endpoint: "/api/v1/admin/webhooks/events", search: "Search event type, message, error…", statusKey: "status", columns: [["createdAt","Created","date"],["eventType","Event","text"],["status","Status","status"],["attemptCount","Attempts","text"],["lastResponseCode","HTTP","status"],["sourceMessageId","Message","id"],["lastError","Last error","text"]] },
  "delivery-attempts": { title: "Delivery attempts", description: "Read-only delivery worker attempts and redacted provider responses.", endpoint: "/api/v1/admin/delivery-attempts", search: "Search message, status, error…", statusKey: "status", columns: [["createdAt","Created","date"],["messageId","Message","id"],["attemptNumber","Attempt","text"],["status","Status","status"],["errorCategory","Error category","status"],["providerResponse","Provider response","text"],["completedAt","Completed","date"]] },
  outbox: { title: "Outbox events", description: "Transactional outbox publication state and retries.", endpoint: "/api/v1/admin/outbox", search: "Search event, aggregate, error…", statusKey: "status", columns: [["createdAt","Created","date"],["eventType","Event","text"],["aggregateType","Aggregate","text"],["status","Status","status"],["attempts","Attempts","text"],["lastError","Last error","text"],["tenantId","Tenant","id"]] },
};

function cell(row: Row, key: string, kind: string) {
  const value = row[key];
  if (kind === "status") return <OpsStatus value={value as string | number | null} />;
  if (kind === "date") return <span className="whitespace-nowrap text-xs text-muted-foreground">{formatOpsDate(value as string | null)}</span>;
  if (kind === "id") return <Mono className="whitespace-nowrap" title={String(value ?? "")}>{shortId(value as string | null)}</Mono>;
  if (kind === "duration") return <Mono>{value == null ? "—" : `${String(value)} ms`}</Mono>;
  if (kind === "dns") {
    const record = value as { status?: string; name?: string | null; selector?: string | null; lastError?: string | null } | null;
    return <div className="min-w-28"><OpsStatus value={record?.status ?? "MISSING"} />{record?.name ? <p className="mt-1 max-w-44 truncate font-mono text-[10px] text-muted-foreground" title={record.name}>{record.name}</p> : null}{record?.selector ? <p className="mt-0.5 text-[10px] text-muted-foreground">selector: {record.selector}</p> : null}{record?.lastError ? <p className="mt-0.5 max-w-44 truncate text-[10px] text-destructive" title={record.lastError}>{record.lastError}</p> : null}</div>;
  }
  return <span className="block max-w-[24rem] truncate" title={String(value ?? "")}>{String(value ?? "—")}</span>;
}

export function AdminCatalogScreen({ kind, compact = false }: { kind: CatalogKind; compact?: boolean }) {
  const router = useRouter();
  const config = CONFIG[kind];
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const resource = useAdminResource(
    () => getOpsPage<Row>(`${config.endpoint}${queryString({ page: page - 1, size: 20, q: kind === "tenants" ? search : undefined })}`),
    [config.endpoint, page, kind === "tenants" ? search : null],
  );
  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (resource.data?.items ?? []).filter((row) => {
      const searchable = JSON.stringify(row).toLowerCase();
      const statusValue = config.statusKey ? String(row[config.statusKey] ?? "").toLowerCase() : "";
      return (!term || searchable.includes(term)) && (status === "all" || statusValue === status.toLowerCase());
    });
  }, [config.statusKey, resource.data?.items, search, status]);
  const statusOptions = useMemo(() => config.statusKey ? Array.from(new Set((resource.data?.items ?? []).map((row) => String(row[config.statusKey!] ?? "")).filter(Boolean))) : [], [config.statusKey, resource.data?.items]);
  const columns: OpsColumn<Row>[] = useMemo(() => config.columns.map(([key, label, type]) => ({ key, label, value: (row) => String(row[key] ?? ""), render: (row) => cell(row, key, type) })), [config.columns]);
  const content = (
    <SectionPanel className="min-w-0 overflow-hidden p-0" title={compact ? undefined : `${resource.data?.totalElements?.toLocaleString() ?? "—"} records`} action={compact ? undefined : <Button type="button" variant="ghost" size="sm" onClick={resource.reload}><RefreshCw className="size-3.5" />Refresh</Button>}>
      <div className="border-b border-border/70 p-4">
        <div className="flex flex-col gap-2 sm:flex-row">
          <label className="relative min-w-0 flex-1"><span className="sr-only">{config.search}</span><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><Input value={search} onChange={(event) => { setSearch(event.target.value); setPage(1); }} placeholder={config.search} className="pl-9" /></label>
          {statusOptions.length ? <Select value={status} onValueChange={setStatus}><SelectTrigger className="w-full sm:w-52"><Filter className="size-3.5" /><SelectValue placeholder="All states" /></SelectTrigger><SelectContent><SelectItem value="all">All states</SelectItem>{statusOptions.map((item) => <SelectItem key={item} value={item}>{item.replaceAll("_", " ")}</SelectItem>)}</SelectContent></Select> : null}
        </div>
      </div>
      <div className="min-h-[18rem]">
        {resource.loading ? <LoadingState rows={6} className="p-4" label={`Loading ${config.title}`} /> : resource.error ? <div className="p-4"><ErrorState description={resource.error} onRetry={resource.reload} /></div> : <OpsTable rows={rows} columns={columns} rowKey={(row) => String(row.id)} onRowClick={kind === "tenants" ? (row) => router.push(`/admin/tenants/${encodeURIComponent(String(row.id))}`) : undefined} emptyTitle={`No ${config.title.toLowerCase()} found`} />}
      </div>
      {!compact && resource.data ? <Pagination className="border-t border-border/70 p-4" page={page} pageCount={resource.data.totalPages} onPageChange={setPage} /> : null}
    </SectionPanel>
  );
  if (compact) return content;
  return <OpsPageFrame title={config.title} description={config.description} actions={kind === "tenants" ? <Button onClick={() => router.push("/admin/accounts/create")}>Add tenant</Button> : undefined}>{content}</OpsPageFrame>;
}

export function DatabaseExplorerScreen() {
  const [dataset, setDataset] = useState<CatalogKind>("delivery-attempts");
  const datasets: Array<{ value: CatalogKind; label: string }> = [
    { value: "delivery-attempts", label: "delivery_attempts" }, { value: "bounces", label: "bounce_events" },
    { value: "complaints", label: "complaint_events" }, { value: "suppressions", label: "suppressions" },
    { value: "tenants", label: "tenants" }, { value: "domains", label: "domains" }, { value: "outbox", label: "outbox" },
    { value: "webhooks", label: "webhook_events" }, { value: "traffic", label: "request_audit_logs" },
  ];
  return <OpsPageFrame eyebrow="Read-only data" title="Database explorer" description="Approved operational datasets only. SQL, credentials, write operations, and sensitive payloads are never exposed.">
    <SectionPanel ambient title="Dataset allow-list" description="Queries use fixed, authenticated API endpoints with bounded pagination.">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center"><div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary"><Database className="size-5" /></div><Select value={dataset} onValueChange={(value) => setDataset(value as CatalogKind)}><SelectTrigger className="w-full sm:w-72" aria-label="Approved dataset"><SelectValue /></SelectTrigger><SelectContent>{datasets.map((item) => <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}</SelectContent></Select><p className="text-xs text-muted-foreground">Read-only · max 100 rows per API request · no arbitrary SQL</p></div>
    </SectionPanel>
    <AdminCatalogScreen kind={dataset} compact />
  </OpsPageFrame>;
}
