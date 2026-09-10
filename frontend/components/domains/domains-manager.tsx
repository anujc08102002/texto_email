"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Globe } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CodeBlock } from "@/components/ui/code-block";
import { CopyButton } from "@/components/ui/copy-button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingState } from "@/components/ui/loading-state";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { SectionPanel } from "@/components/ops/section-panel";
import { ApiClientError } from "@/lib/api";
import { DomainAuthStatus } from "@/components/domains/domain-auth-status";
import { createDomain, getDomainVerification, listDomains, verifyDomain } from "@/services/domains";
import type { Domain, DomainVerification } from "@/types/api";
import { cn } from "@/lib/utils";

export function DomainsManager() {
  const [domains, setDomains] = useState<Domain[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [verification, setVerification] = useState<DomainVerification | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [domainInput, setDomainInput] = useState("");
  const [creating, setCreating] = useState(false);
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listDomains()
      .then(async (data) => {
        if (cancelled) return;
        setDomains(data);
        setError(null);
        const nextId = data[0]?.id ?? null;
        setSelectedId(nextId);
        if (nextId) {
          setVerification(await getDomainVerification(nextId));
        } else {
          setVerification(null);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load domains.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const data = await listDomains();
      setDomains(data);
      const nextId = selectedId && data.some((d) => d.id === selectedId) ? selectedId : data[0]?.id ?? null;
      setSelectedId(nextId);
      if (nextId) {
        setVerification(await getDomainVerification(nextId));
      } else {
        setVerification(null);
      }
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load domains.");
    } finally {
      setLoading(false);
    }
  }

  async function selectDomain(id: string) {
    setSelectedId(id);
    try {
      setVerification(await getDomainVerification(id));
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to load DNS records.");
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button>Add domain</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Add sending domain</DialogTitle>
              <DialogDescription>
                We generate a dedicated ownership TXT plus separate SPF, DKIM, and DMARC records.
                Verification checks live DNS. The platform test domain is provisioned automatically.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-1.5">
              <Label htmlFor="domain">Domain</Label>
              <Input
                id="domain"
                placeholder="mail.example.com"
                value={domainInput}
                onChange={(e) => setDomainInput(e.target.value)}
                className="font-mono"
              />
            </div>
            <DialogFooter>
              <Button
                loading={creating}
                onClick={async () => {
                  setCreating(true);
                  try {
                    const created = await createDomain(domainInput.trim());
                    toast.success("Domain added");
                    setOpen(false);
                    setDomainInput("");
                    await reload();
                    await selectDomain(created.id);
                  } catch (cause) {
                    toast.error(cause instanceof ApiClientError ? cause.message : "Unable to add domain.");
                  } finally {
                    setCreating(false);
                  }
                }}
              >
                Continue
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {loading ? <LoadingState label="Loading domains" /> : null}
      {error ? <ErrorState description={error} onRetry={reload} /> : null}
      {!loading && !error && domains.length === 0 ? (
        <EmptyState
          icon={<Globe className="size-5" />}
          title="Add your first sending domain"
          description="Verify DNS before sending from custom from-addresses."
        />
      ) : null}

      {!loading && !error && domains.length > 0 ? (
        <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
          <SectionPanel title="Domains">
            <ul className="space-y-2">
              {domains.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => void selectDomain(item.id)}
                    className={cn(
                      "w-full rounded-lg border px-3 py-2.5 text-left transition-colors",
                      selectedId === item.id
                        ? "border-primary/40 bg-primary/5"
                        : "border-border/70 hover:bg-muted/40",
                    )}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-mono text-sm">{item.domain}</p>
                      <Badge variant={item.verificationStatus === "VERIFIED" ? "success" : "warning"}>
                        {item.verificationStatus}
                      </Badge>
                    </div>
                    <p className="mt-1 text-caption">Status {item.status}</p>
                  </button>
                </li>
              ))}
            </ul>
          </SectionPanel>

          <SectionPanel
            title="DNS verification"
            description="Publish these records, then verify"
            action={
              selectedId ? (
                <div className="flex gap-2">
                  <Button asChild size="sm" variant="ghost">
                    <Link href={`/domains/${selectedId}`}>Open</Link>
                  </Button>
                  <Button
                    size="sm"
                    loading={verifying}
                    onClick={async () => {
                      if (!selectedId) return;
                      setVerifying(true);
                      try {
                        const updated = await verifyDomain(selectedId);
                        toast.message(`Verification ${updated.verificationStatus}`);
                        await reload();
                        await selectDomain(selectedId);
                      } catch (cause) {
                        toast.error(cause instanceof ApiClientError ? cause.message : "Verify failed");
                      } finally {
                        setVerifying(false);
                      }
                    }}
                  >
                    Verify DNS
                  </Button>
                </div>
              ) : null
            }
          >
            {verification ? (
              <div className="space-y-4">
                <DomainAuthStatus verification={verification} />
                <ul className="space-y-3">
                  {verification.records.map((record) => (
                    <li key={record.id} className="rounded-lg border border-border/70 p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-semibold">
                          {record.type} · <span className="font-mono text-xs font-normal">{record.name}</span>
                        </p>
                        <Badge variant={record.status === "VERIFIED" ? "success" : "secondary"}>{record.status}</Badge>
                      </div>
                      {record.detail ? (
                        <p className="mt-1 text-xs text-muted-foreground">{record.detail}</p>
                      ) : null}
                      <div className="mt-2 flex items-start gap-2">
                        <div className="min-w-0 flex-1">
                          <CodeBlock code={record.value} />
                        </div>
                        <CopyButton value={record.value} />
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Select a domain to view DNS records.</p>
            )}
          </SectionPanel>
        </div>
      ) : null}
    </div>
  );
}
