"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { EmailTable } from "@/components/emails/email-table";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiClientError } from "@/lib/api";
import { listEmails } from "@/services/emails";
import type { EmailMessagePage } from "@/types/api";

export default function EmailsPage() {
  const [data, setData] = useState<EmailMessagePage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    const handle = window.setTimeout(async () => {
      setLoading(true); setError(null);
      try { const result = await listEmails({ page, size, status, q: query }); if (active) setData(result); }
      catch (cause) { if (active) setError(cause instanceof ApiClientError ? cause.message : "Unable to load emails."); }
      finally { if (active) setLoading(false); }
    }, 250);
    return () => { active = false; window.clearTimeout(handle); };
  }, [page, size, status, query, revision]);
  return <div className="flex min-h-full flex-col gap-4">
    <PageHeader title="Emails" description="Messages and delivery activity from your workspace." actions={<Button asChild><Link href="/emails/new"><Plus className="size-4" /> Compose</Link></Button>} />
    {error ? <ErrorState description={error} onRetry={() => setRevision(value => value + 1)} /> : null}
    <EmailTable messages={data?.items ?? []} loading={loading} server={{ page, pageSize: size, total: data?.totalElements ?? 0, search: query, onPageChange: setPage, onPageSizeChange: value => { setSize(value); setPage(0); }, onSearchChange: value => { setQuery(value); setPage(0); }, sort: { key: "createdAt", direction: "desc" }, onSortChange: () => {} }} toolbarActions={<Select value={status} onValueChange={value => { setStatus(value); setPage(0); }}><SelectTrigger className="w-40" aria-label="Filter by status"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">All statuses</SelectItem>{["QUEUED", "PROCESSING", "SENDING", "SENT", "DELIVERED", "DEFERRED", "FAILED", "BOUNCED", "SUPPRESSED", "CANCELLED", "EXPIRED"].map(value => <SelectItem key={value} value={value}>{value}</SelectItem>)}</SelectContent></Select>} />
  </div>;
}
