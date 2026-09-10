"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { FileCode2, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatDateTime } from "@/lib/format";
import type { Template } from "@/types/api";

function statusVariant(status: string) {
  if (status === "ACTIVE") return "success" as const;
  if (status === "ARCHIVED") return "secondary" as const;
  return "warning" as const;
}

export function TemplatesList({ templates }: { templates: Template[] }) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");

  const filtered = useMemo(() => {
    return templates.filter((template) => {
      const q = query.trim().toLowerCase();
      const matchesQuery =
        !q ||
        template.name.toLowerCase().includes(q) ||
        template.slug.toLowerCase().includes(q) ||
        (template.description ?? "").toLowerCase().includes(q);
      const matchesStatus = status === "all" || template.status === status;
      return matchesQuery && matchesStatus;
    });
  }, [templates, query, status]);

  if (templates.length === 0) {
    return (
      <EmptyState
        icon={<FileCode2 className="size-5" />}
        title="Create your first template"
        description="Reusable HTML and text templates for transactional sends."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search name or slug"
            className="pl-9"
            aria-label="Search templates"
          />
        </div>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-44" aria-label="Filter by status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="ARCHIVED">Archived</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {filtered.length === 0 ? (
        <EmptyState title="No templates match" description="Try a different search or status filter." />
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Version</TableHead>
                <TableHead>Updated</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((template) => (
                <TableRow key={template.id}>
                  <TableCell>
                    <Link href={`/templates/${template.id}`} className="font-medium hover:text-primary">
                      {template.name}
                    </Link>
                    <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">{template.slug}</p>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(template.status)}>{template.status}</Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    {template.currentVersion != null ? `v${template.currentVersion}` : "—"}
                  </TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {formatDateTime(template.updatedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
