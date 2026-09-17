"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Mail, Search } from "lucide-react";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { LoadingState } from "@/components/ui/loading-state";
import { Pagination } from "@/components/ui/pagination";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiClientError } from "@/lib/api";
import { formatRelativeTime, initials } from "@/lib/format";
import { listEmails } from "@/services/emails";
import type { EmailMessage } from "@/types/api";

export default function EmailsPage() {
  const [messages, setMessages] = useState<EmailMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  function reload(nextPage = page, nextStatus = status, nextQuery = query) {
    setLoading(true);
    setError(null);
    listEmails({ page: nextPage, size: 20, status: nextStatus, q: nextQuery })
      .then((data) => {
        setMessages(data.items);
        setTotalPages(Math.max(1, data.totalPages));
        setPage(data.page);
      })
      .catch((cause) => {
        setError(cause instanceof ApiClientError ? cause.message : "Unable to load emails.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    const handle = window.setTimeout(() => reload(0, status, query), 250);
    return () => window.clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, query]);

  return (
    <div className="flex h-full min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Workspace"
        title="Emails"
        description="Delivery activity. Status advances QUEUED → PROCESSING → SENDING → DELIVERED."
        actions={
          <Button asChild>
            <Link href="/emails/new">Compose</Link>
          </Button>
        }
      />

      <Alert variant="info" className="hidden sm:flex">
        <AlertDescription>
          Local delivery lands in Mailpit{" "}
          <a className="underline" href="http://localhost:8025" target="_blank" rel="noreferrer">
            http://localhost:8025
          </a>
          .
        </AlertDescription>
      </Alert>

      <SectionPanel className="flex min-h-0 flex-1 flex-col">
        <div className="mb-5 flex flex-col gap-3 sm:flex-row">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search recipient, subject, or message ID"
              className="h-10 rounded-full pl-10"
              aria-label="Search emails"
            />
          </div>
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger className="rounded-full sm:w-44" aria-label="Filter by status">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All statuses</SelectItem>
              <SelectItem value="QUEUED">Queued</SelectItem>
              <SelectItem value="PROCESSING">Processing</SelectItem>
              <SelectItem value="SENDING">Sending</SelectItem>
              <SelectItem value="DELIVERED">Delivered</SelectItem>
              <SelectItem value="DEFERRED">Deferred</SelectItem>
              <SelectItem value="FAILED">Failed</SelectItem>
              <SelectItem value="BOUNCED">Bounced</SelectItem>
              <SelectItem value="SUPPRESSED">Suppressed</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {loading ? <LoadingState label="Loading emails" /> : null}
        {error ? <ErrorState description={error} onRetry={() => reload()} /> : null}
        {!loading && !error && messages.length === 0 ? (
          <div className="flex flex-1 items-center justify-center py-10">
          <EmptyState
            icon={<Mail className="size-5" />}
            title="Send your first email"
            description="Compose a message to queue delivery through the pipeline."
            action={
              <Button asChild>
                <Link href="/emails/new">Compose</Link>
              </Button>
            }
          />
          </div>
        ) : null}
        {!loading && !error && messages.length > 0 ? (
          <>
            <ul className="divide-y divide-border/70 overflow-hidden rounded-xl border border-border/70 bg-card">
              {messages.map((message) => (
                <li key={message.id}>
                  <Link
                    href={`/emails/${message.id}`}
                    className="flex items-center gap-3 px-4 py-3.5 transition-colors hover:bg-muted/50"
                  >
                    <Avatar className="size-9">
                      <AvatarFallback>{initials(message.recipient)}</AvatarFallback>
                    </Avatar>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-medium">{message.subject || "(no subject)"}</p>
                        <EmailStatusBadge status={message.status} />
                      </div>
                      <p className="mt-0.5 truncate text-sm text-muted-foreground">{message.recipient}</p>
                    </div>
                    <p className="hidden shrink-0 text-xs text-muted-foreground sm:block">
                      {formatRelativeTime(message.createdAt)}
                    </p>
                  </Link>
                </li>
              ))}
            </ul>

            <Pagination
              page={page + 1}
              pageCount={totalPages}
              onPageChange={(next) => reload(next - 1, status, query)}
            />
          </>
        ) : null}
      </SectionPanel>
    </div>
  );
}
