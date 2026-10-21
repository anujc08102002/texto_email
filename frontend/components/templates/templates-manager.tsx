"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Plus } from "lucide-react";
import DataGrid from "@/components/data-grid/DataGrid";
import type { DataGridColumn } from "@/components/data-grid/types";
import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { listTemplates } from "@/services/templates";
import type { Template } from "@/types/api";

const columns: DataGridColumn<Template>[] = [
  { key: "name", label: "Template Name", sortable: true, filterable: true, fixed: true, render: row => <Link href={`/templates/${row.id}`} className="font-medium text-green-600 hover:underline">{row.name}</Link> },
  { key: "slug", label: "Slug", sortable: true, filterable: true },
  { key: "status", label: "Status", sortable: true, filterable: true, render: row => <Badge variant={row.status === "ACTIVE" ? "success" : "secondary"}>{row.status}</Badge> },
  { key: "currentVersion", label: "Version", sortable: true, sortType: "number", render: row => <span>{row.currentVersion != null ? `v${row.currentVersion}` : "—"}</span> },
  { key: "updatedAt", label: "Updated At", sortable: true, sortType: "date", render: row => <span>{formatDateTime(row.updatedAt)}</span> },
  { key: "actions", label: "Actions", fixed: true, render: row => <Button variant="outline" size="sm" asChild><Link href={`/templates/${row.id}`}>View</Link></Button> },
];

export function TemplatesManager() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const reload = useCallback(async () => {
    setLoading(true); setError(null);
    try { setTemplates(await listTemplates()); }
    catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "Unable to load templates."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let active = true;
    listTemplates()
      .then(data => { if (active) setTemplates(data); })
      .catch(cause => { if (active) setError(cause instanceof ApiClientError ? cause.message : "Unable to load templates."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <div className="flex min-h-full flex-col gap-4">
    <PageHeader title="Templates" actions={<Button asChild><Link href="/templates/new"><Plus className="size-4" /> Add Template</Link></Button>} />
    {error ? <ErrorState description={error} onRetry={reload} /> : <DataGrid pageName="email-templates" columns={columns} rows={templates} rowKey={row => row.id} entityName="Templates" loading={loading} searchPlaceholder="Search templates..." emptyTitle="No templates found" emptyMessage="Create your first email template to get started." />}
  </div>;
}
