"use client";

import Link from "next/link";
import DataGrid from "@/components/data-grid/DataGrid";
import type { ReactNode } from "react";
import type { DataGridColumn, DataGridProps } from "@/components/data-grid/types";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { Button } from "@/components/ui/button";
import { formatDateTime } from "@/lib/format";
import type { EmailMessage } from "@/types/api";

const columns: DataGridColumn<EmailMessage>[] = [
  { key: "recipient", label: "Recipient", sortable: true, filterable: true, fixed: true, render: row => <Link href={`/emails/${row.id}`} className="font-medium text-green-600 hover:underline">{row.recipient}</Link> },
  { key: "subject", label: "Subject", sortable: true, filterable: true },
  { key: "status", label: "Status", sortable: true, filterable: true, render: row => <EmailStatusBadge status={row.status} /> },
  { key: "createdAt", label: "Created At", sortable: true, sortType: "date", render: row => formatDateTime(row.createdAt) },
  { key: "id", label: "Message ID", sortable: true },
  { key: "actions", label: "Actions", fixed: true, render: row => <Button asChild variant="outline" size="sm"><Link href={`/emails/${row.id}`}>View</Link></Button> },
];
export function EmailTable({ messages, server, loading, toolbarActions }: { messages: EmailMessage[]; server?: DataGridProps<EmailMessage>["server"]; loading?: boolean; toolbarActions?: ReactNode }) {
  return <DataGrid pageName="email-messages" columns={server ? columns.map(column => ({ ...column, sortable: false, filterable: false })) : columns} rows={messages} rowKey={row => row.id} entityName="Emails" server={server} loading={loading} totalCountLabel={server?.total ?? messages.length} toolbarActions={toolbarActions} searchPlaceholder="Search recipient, subject, or message ID" emptyTitle="No emails found" emptyMessage="Send your first email or adjust your filters." />;
}
