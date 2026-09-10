"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Mail, Search } from "lucide-react";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
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
    <div className="space-y-5">
      <PageHeader
        eyebrow="Workspace"
        title="Emails"
        description="Async delivery activity. Status advances QUEUED → PROCESSING → SENDING → DELIVERED."
        actions={
          <Button asChild>
            <Link href="/emails/new">Compose</Link>
          </Button>
        }
      />

      <Alert variant="info">
        <AlertDescription>
          Local delivery lands in Mailpit{" "}
          <a className="underline" href="http://localhost:8025" target="_blank" rel="noreferrer">
            http://localhost:8025
          </a>
          .
        </AlertDescription>
      </Alert>

      <SectionPanel>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search recipient, subject, or message ID"
              className="pl-9"
              aria-label="Search emails"
            />
          </div>
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger className="sm:w-44" aria-label="Filter by status">
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
        ) : null}
        {!loading && !error && messages.length > 0 ? (
          <>
            <div className="hidden overflow-hidden rounded-lg border border-border bg-card md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Message ID</TableHead>
                    <TableHead>Recipient</TableHead>
                    <TableHead>Subject</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Created</TableHead>
                    <TableHead>Delivered</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {messages.map((message) => (
                    <TableRow key={message.id}>
                      <TableCell>
                        <Link href={`/emails/${message.id}`} className="font-mono text-xs hover:text-primary">
                          {message.id}
                        </Link>
                      </TableCell>
                      <TableCell className="font-mono text-xs">{message.recipient}</TableCell>
                      <TableCell className="max-w-xs truncate">{message.subject}</TableCell>
                      <TableCell>
                        <EmailStatusBadge status={message.status} />
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {formatDateTime(message.createdAt)}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {message.deliveredAt ? formatDateTime(message.deliveredAt) : "—"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="space-y-3 md:hidden">
              {messages.map((message) => (
                <Link key={message.id} href={`/emails/${message.id}`}>
                  <Card className="p-4 transition-colors hover:bg-muted/40">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate font-mono text-xs">{message.id}</p>
                        <p className="mt-1 truncate text-sm font-medium">{message.recipient}</p>
                        <p className="mt-1 truncate text-sm text-muted-foreground">{message.subject}</p>
                      </div>
                      <EmailStatusBadge status={message.status} />
                    </div>
                  </Card>
                </Link>
              ))}
            </div>

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
