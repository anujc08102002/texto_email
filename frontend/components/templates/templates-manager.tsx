"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { FileCode2, Search } from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { listTemplates } from "@/services/templates";
import type { Template } from "@/types/api";

export function TemplatesManager() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  useEffect(() => {
    let cancelled = false;
    listTemplates()
      .then((data) => {
        if (!cancelled) {
          setTemplates(data);
          setError(null);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load templates.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return templates;
    return templates.filter(
      (item) =>
        item.name.toLowerCase().includes(q) ||
        item.slug.toLowerCase().includes(q) ||
        item.status.toLowerCase().includes(q),
    );
  }, [templates, query]);

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Workspace"
        title="Templates"
        description="Versioned HTML/text templates with controlled {{variable}} substitution."
        actions={
          <Button asChild>
            <Link href="/templates/new">New template</Link>
          </Button>
        }
      />

      <SectionPanel>
        <div className="mb-4">
          <div className="relative max-w-md">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search name, slug, or status"
              className="pl-9"
              aria-label="Search templates"
            />
          </div>
        </div>

        {loading ? <LoadingState label="Loading templates" /> : null}
        {error ? (
          <ErrorState
            description={error}
            onRetry={() => {
              setLoading(true);
              setError(null);
              listTemplates()
                .then(setTemplates)
                .catch((cause) => {
                  setError(cause instanceof ApiClientError ? cause.message : "Unable to load templates.");
                })
                .finally(() => setLoading(false));
            }}
          />
        ) : null}
        {!loading && !error && filtered.length === 0 ? (
          <EmptyState
            icon={<FileCode2 className="size-5" />}
            title="Create your first template"
            description="Define subject, HTML, text, and a variables schema for transactional sends."
            action={
              <Button asChild>
                <Link href="/templates/new">New template</Link>
              </Button>
            }
          />
        ) : null}
        {!loading && !error && filtered.length > 0 ? (
          <ul className="divide-y divide-border/70">
            {filtered.map((template) => (
              <li key={template.id} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0">
                  <Link href={`/templates/${template.id}`} className="text-sm font-medium hover:text-primary">
                    {template.name}
                  </Link>
                  <p className="mt-0.5 text-tech text-muted-foreground">
                    {template.slug} · v{template.currentVersion ?? "—"} · {formatDateTime(template.updatedAt)}
                  </p>
                </div>
                <Badge variant={template.status === "ACTIVE" ? "success" : "secondary"}>{template.status}</Badge>
              </li>
            ))}
          </ul>
        ) : null}
      </SectionPanel>
    </div>
  );
}
