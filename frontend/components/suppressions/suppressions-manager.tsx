"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { toast } from "sonner";
import { Ban, Search } from "lucide-react";
import { UpgradeHint } from "@/components/billing/upgrade-hint";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingState } from "@/components/ui/loading-state";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { getEntitlements } from "@/services/platform";
import {
  createSuppression,
  deleteSuppression,
  importSuppressions,
  listSuppressions,
} from "@/services/suppressions";
import type { Suppression } from "@/types/api";

function typeVariant(type: string) {
  if (type === "BOUNCE") return "error" as const;
  if (type === "COMPLAINT") return "warning" as const;
  if (type === "MANUAL") return "info" as const;
  return "secondary" as const;
}

export function SuppressionsManager() {
  const [items, setItems] = useState<Suppression[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [type, setType] = useState("all");
  const [allowed, setAllowed] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [reason, setReason] = useState("");
  const [importText, setImportText] = useState("");
  const [creating, setCreating] = useState(false);
  const [importing, setImporting] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [data, entitlements] = await Promise.all([
        listSuppressions({ search, type }),
        getEntitlements().catch(() => null),
      ]);
      setItems(data);
      if (entitlements) {
        setAllowed(entitlements.features.SUPPRESSION !== false);
      }
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load suppressions.");
    } finally {
      setLoading(false);
    }
  }, [search, type]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void reload();
    }, 250);
    return () => window.clearTimeout(handle);
  }, [reload]);

  async function onCreate() {
    setCreating(true);
    try {
      await createSuppression({
        email: email.trim(),
        reason: reason.trim() || undefined,
      });
      toast.success("Address suppressed");
      setCreateOpen(false);
      setEmail("");
      setReason("");
      await reload();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to create suppression.");
    } finally {
      setCreating(false);
    }
  }

  async function onImport() {
    const emails = importText
      .split(/[\n,;]+/)
      .map((value) => value.trim())
      .filter(Boolean);
    if (emails.length === 0) {
      toast.error("Add at least one email address.");
      return;
    }
    setImporting(true);
    try {
      const result = await importSuppressions(emails);
      toast.success(`Imported ${result.imported}, skipped ${result.skipped}`);
      setImportOpen(false);
      setImportText("");
      await reload();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to import suppressions.");
    } finally {
      setImporting(false);
    }
  }

  async function onRemove(id: string) {
    setRemovingId(id);
    try {
      await deleteSuppression(id);
      toast.success("Suppression removed");
      await reload();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to remove suppression.");
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <div className="space-y-4">
      {!allowed ? <UpgradeHint feature="Suppressions" /> : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-1 flex-col gap-3 sm:flex-row">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search email"
              className="pl-9"
              aria-label="Search suppressions"
            />
          </div>
          <Select value={type} onValueChange={setType}>
            <SelectTrigger className="sm:w-44" aria-label="Filter by type">
              <SelectValue placeholder="Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All types</SelectItem>
              <SelectItem value="BOUNCE">Bounce</SelectItem>
              <SelectItem value="COMPLAINT">Complaint</SelectItem>
              <SelectItem value="UNSUBSCRIBE">Unsubscribe</SelectItem>
              <SelectItem value="MANUAL">Manual</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-wrap gap-2">
          <Dialog open={importOpen} onOpenChange={setImportOpen}>
            <DialogTrigger asChild>
              <Button type="button" variant="secondary" disabled={!allowed}>
                Import
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Import suppressions</DialogTitle>
                <DialogDescription>One email per line, or comma-separated.</DialogDescription>
              </DialogHeader>
              <Textarea
                value={importText}
                onChange={(event) => setImportText(event.target.value)}
                className="min-h-40 font-mono text-xs"
                placeholder={"user@example.com\nanother@example.com"}
              />
              <DialogFooter>
                <Button type="button" loading={importing} onClick={() => void onImport()}>
                  Import
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <Button type="button" disabled={!allowed}>
                Add manual
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Suppress address</DialogTitle>
                <DialogDescription>Creates a MANUAL suppression entry.</DialogDescription>
              </DialogHeader>
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <Label htmlFor="suppression-email">Email</Label>
                  <Input
                    id="suppression-email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    className="font-mono text-sm"
                    required
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="suppression-reason">Reason</Label>
                  <Input
                    id="suppression-reason"
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder="Optional"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button
                  type="button"
                  loading={creating}
                  disabled={!email.trim()}
                  onClick={() => void onCreate()}
                >
                  Suppress
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {loading ? <LoadingState label="Loading suppressions" /> : null}
      {error ? <ErrorState description={error} onRetry={() => void reload()} /> : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState
          icon={<Ban className="size-5" />}
          title="No suppressions"
          description="Bounces, complaints, and manual blocks will appear here."
          action={
            !allowed ? (
              <Button asChild variant="secondary">
                <Link href="/billing">Upgrade</Link>
              </Button>
            ) : undefined
          }
        />
      ) : null}

      {!loading && !error && items.length > 0 ? (
        <SectionPanel title="Suppression list" description="Honored before outbound delivery">
          <div className="overflow-hidden rounded-lg border border-border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Email</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Reason</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead className="w-[1%]" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-mono text-xs">{item.email}</TableCell>
                    <TableCell>
                      <Badge variant={typeVariant(item.type)}>{item.type}</Badge>
                    </TableCell>
                    <TableCell className="max-w-xs truncate text-sm text-muted-foreground">
                      {item.reason || "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {formatDateTime(item.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        loading={removingId === item.id}
                        onClick={() => void onRemove(item.id)}
                      >
                        Remove
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </SectionPanel>
      ) : null}
    </div>
  );
}
