"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Search } from "lucide-react";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Pagination } from "@/components/ui/pagination";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatDateTime } from "@/lib/format";
import type { EmailMessage } from "@/types/api";

const PAGE_SIZE = 10;

export function EmailTable({ messages }: { messages: EmailMessage[] }) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);

  const filtered = useMemo(() => {
    return messages.filter((message) => {
      const matchesQuery =
        !query ||
        message.recipient.toLowerCase().includes(query.toLowerCase()) ||
        message.subject.toLowerCase().includes(query.toLowerCase()) ||
        message.id.toLowerCase().includes(query.toLowerCase());
      const matchesStatus = status === "all" || message.status === status;
      return matchesQuery && matchesStatus;
    });
  }, [messages, query, status]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount);
  const visible = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(1);
            }}
            placeholder="Search recipient, subject, or message ID"
            className="pl-9"
            aria-label="Search emails"
          />
        </div>
        <Select
          value={status}
          onValueChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
        >
          <SelectTrigger className="sm:w-44" aria-label="Filter by status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            <SelectItem value="QUEUED">Queued</SelectItem>
            <SelectItem value="SENT">Sent</SelectItem>
            <SelectItem value="FAILED">Failed</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="hidden md:block overflow-hidden rounded-lg border border-border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Recipient</TableHead>
              <TableHead>Subject</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Created</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visible.map((message) => (
              <TableRow key={message.id}>
                <TableCell>
                  <Link href={`/emails/${message.id}`} className="font-medium hover:text-primary">
                    {message.recipient}
                  </Link>
                </TableCell>
                <TableCell className="max-w-xs truncate">{message.subject}</TableCell>
                <TableCell>
                  <EmailStatusBadge status={message.status} />
                </TableCell>
                <TableCell className="font-mono text-xs text-muted-foreground">
                  {formatDateTime(message.createdAt)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div className="space-y-3 md:hidden">
        {visible.map((message) => (
          <Link key={message.id} href={`/emails/${message.id}`}>
            <Card className="p-4 transition-colors hover:bg-muted/40">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{message.recipient}</p>
                  <p className="mt-1 truncate text-sm text-muted-foreground">{message.subject}</p>
                </div>
                <EmailStatusBadge status={message.status} />
              </div>
              <p className="mt-2 font-mono text-xs text-muted-foreground">{formatDateTime(message.createdAt)}</p>
            </Card>
          </Link>
        ))}
      </div>

      <Pagination page={currentPage} pageCount={pageCount} onPageChange={setPage} />
    </div>
  );
}
