"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import DataGrid from "@/components/data-grid/DataGrid";
import type { SortState } from "@/components/data-grid/types";
import { ErrorState } from "@/components/ui/error-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { listCampaigns, type CampaignPage } from "@/services/campaigns";

const statuses = ["DRAFT", "SCHEDULED", "QUEUED", "PROCESSING", "COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "STOPPED", "CANCELLED"];

export function CampaignsWorkspace() {
  const requestId = useRef(0);
  const [data, setData] = useState<CampaignPage | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ key: "updatedAt", direction: "desc" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listCampaigns(page, size, query, status, sort.key, sort.direction);
      if (currentRequest === requestId.current) setData(result);
    } catch (cause) {
      if (currentRequest !== requestId.current) return;
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load campaigns.");
    } finally {
      if (currentRequest === requestId.current) setLoading(false);
    }
  }, [page, query, size, status, sort]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 200);
    return () => window.clearTimeout(timer);
  }, [load]);

  return (
    <div className="space-y-4">
      {error ? <ErrorState description={error} onRetry={() => void load()} /> : null}
      <DataGrid pageName="email-campaigns" rows={data?.items ?? []} rowKey={(campaign) => campaign.id}
        loading={loading} entityName="Campaigns" totalCountLabel={data?.totalElements ?? 0}
        searchPlaceholder="Search campaigns" emptyTitle="No campaigns found"
        emptyMessage="Create a campaign or adjust your search."
        server={{ page, pageSize: size, total: data?.totalElements ?? 0, search: query,
          onPageChange: setPage, onPageSizeChange: (value) => { setSize(value); setPage(0); },
          onSearchChange: (value) => { setQuery(value); setPage(0); }, sort,
          onSortChange: (value) => { setSort(value); setPage(0); } }}
        toolbarActions={<select aria-label="Filter campaign status" className="h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
          <option value="">All statuses</option>
          {statuses.map((value) => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}
        </select>}
        columns={[
          { key: "name", label: "Name", fixed: true, sortable: true, render: (campaign) => <Link href={`/campaigns/${campaign.id}`} className="font-medium text-primary hover:underline">{campaign.name}</Link> },
          { key: "status", label: "Status", sortable: true, render: (campaign) => <span className="rounded-full border px-2 py-1 text-xs">{campaign.status.replaceAll("_", " ")}</span> },
          { key: "subject", label: "Subject", sortable: true, render: (campaign) => campaign.subject || (campaign.templateId ? "Template content" : "-") },
          { key: "scheduledAt", label: "Scheduled", sortable: true, render: (campaign) => campaign.scheduledAt ? formatDateTime(campaign.scheduledAt) : "-" },
          { key: "updatedAt", label: "Updated", sortable: true, render: (campaign) => formatDateTime(campaign.updatedAt) },
        ]}
      />
    </div>
  );
}
