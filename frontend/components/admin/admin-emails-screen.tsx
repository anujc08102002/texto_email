"use client";

import { type FormEvent, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { useRouter } from "next/navigation";
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
import type { OpsEmail } from "@/types/ops";

const statuses = ["all", "QUEUED", "PROCESSING", "SENDING", "DELIVERED", "DEFERRED", "FAILED", "BOUNCED", "SUPPRESSED"];
const columns: OpsColumn<OpsEmail>[] = [
  { key: "createdAt", label: "Created", value: (row) => row.createdAt, render: (row) => <span className="whitespace-nowrap text-xs text-muted-foreground">{formatOpsDate(row.createdAt)}</span> },
  { key: "status", label: "Status", value: (row) => row.status, render: (row) => <OpsStatus value={row.status} /> },
  { key: "from", label: "Sender", value: (row) => row.fromAddress, render: (row) => <span className="block max-w-52 truncate" title={row.fromAddress}>{row.fromAddress}</span> },
  { key: "recipient", label: "Recipient", value: (row) => row.recipient, render: (row) => <span className="block max-w-52 truncate" title={row.recipient}>{row.recipient}</span> },
  { key: "provider", label: "Provider message ID", value: (row) => row.providerMessageId, render: (row) => <Mono title={row.providerMessageId ?? ""}>{shortId(row.providerMessageId)}</Mono> },
  { key: "attempts", label: "Attempts", value: (row) => row.attemptCount ?? 0, render: (row) => <Mono>{row.attemptCount ?? 0}/{row.maxAttempts ?? "—"}</Mono> },
  { key: "id", label: "Message ID", value: (row) => row.id, render: (row) => <Mono title={row.id}>{shortId(row.id)}</Mono> },
];

export function AdminEmailsScreen() {
  const router = useRouter();
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState("all");
  const [search, setSearch] = useState("");
  const [draftSearch, setDraftSearch] = useState("");
  const [field, setField] = useState("recipient");
  const resource = useAdminResource(() => getOpsPage<OpsEmail>(`/api/v1/admin/emails${queryString({ page: page - 1, size: 20, status: status === "all" ? undefined : status, [field]: search })}`), [page, status, search, field]);
  function applySearch(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setSearch(draftSearch.trim()); setPage(1); }
  return <OpsPageFrame title="Email explorer" description="Search and inspect message lifecycle metadata across every tenant. Message bodies are not exposed.">
    <SectionPanel className="min-w-0 overflow-hidden p-0" title={`${resource.data?.totalElements?.toLocaleString() ?? "—"} messages`} action={<Button type="button" variant="ghost" size="sm" onClick={resource.reload}><RefreshCw className="size-3.5" />Refresh</Button>}>
      <form onSubmit={applySearch} className="grid gap-2 border-b border-border/70 p-4 md:grid-cols-[10rem_minmax(15rem,1fr)_auto_12rem]">
        <Select value={field} onValueChange={(value) => { setField(value); setSearch(""); setDraftSearch(""); setPage(1); }}><SelectTrigger aria-label="Search field"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="recipient">Recipient</SelectItem><SelectItem value="sender">Sender</SelectItem><SelectItem value="providerMessageId">Provider ID</SelectItem><SelectItem value="messageId">Message ID</SelectItem><SelectItem value="tenantId">Tenant ID</SelectItem></SelectContent></Select>
        <label className="relative"><span className="sr-only">Search email messages</span><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><Input className="pl-9" value={draftSearch} onChange={(event) => setDraftSearch(event.target.value)} placeholder={`Search by ${field.replace(/([A-Z])/g, " $1").toLowerCase()}…`} /></label>
        <Button type="submit" variant="secondary">Search</Button>
        <Select value={status} onValueChange={(value) => { setStatus(value); setPage(1); }}><SelectTrigger aria-label="Message status"><SelectValue /></SelectTrigger><SelectContent>{statuses.map((item) => <SelectItem key={item} value={item}>{item === "all" ? "All statuses" : item}</SelectItem>)}</SelectContent></Select>
      </form>
      <div className="min-h-[22rem]">{resource.loading ? <LoadingState rows={7} className="p-4" label="Loading email messages" /> : resource.error ? <div className="p-4"><ErrorState description={resource.error} onRetry={resource.reload} /></div> : <OpsTable rows={resource.data?.items ?? []} columns={columns} rowKey={(row) => row.id} onRowClick={(row) => router.push(`/admin/emails/${row.id}`)} emptyTitle="No messages found" />}</div>
      {resource.data ? <Pagination className="border-t border-border/70 p-4" page={page} pageCount={resource.data.totalPages} onPageChange={setPage} /> : null}
    </SectionPanel>
  </OpsPageFrame>;
}
