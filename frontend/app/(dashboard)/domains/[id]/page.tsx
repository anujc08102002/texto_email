"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CodeBlock } from "@/components/ui/code-block";
import { CopyButton } from "@/components/ui/copy-button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { getDomain, getDomainVerification, verifyDomain } from "@/services/domains";
import type { Domain, DomainVerification } from "@/types/api";

export default function DomainDetailPage() {
  const params = useParams<{ id: string }>();
  const [domain, setDomain] = useState<Domain | null>(null);
  const [verification, setVerification] = useState<DomainVerification | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getDomain(params.id), getDomainVerification(params.id)])
      .then(([d, v]) => {
        if (cancelled) return;
        setDomain(d);
        setVerification(v);
        setError(null);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load domain.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [params.id]);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const [d, v] = await Promise.all([getDomain(params.id), getDomainVerification(params.id)]);
      setDomain(d);
      setVerification(v);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load domain.");
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <LoadingState label="Loading domain" />;
  if (error || !domain) return <ErrorState description={error ?? "Not found"} onRetry={reload} />;

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Workspace"
        title={domain.domain}
        description={`Status ${domain.status} · verification ${domain.verificationStatus}`}
        actions={
          <Button
            loading={verifying}
            onClick={async () => {
              setVerifying(true);
              try {
                const updated = await verifyDomain(domain.id);
                toast.message(`Verification ${updated.verificationStatus}`);
                await reload();
              } catch (cause) {
                toast.error(cause instanceof ApiClientError ? cause.message : "Verify failed");
              } finally {
                setVerifying(false);
              }
            }}
          >
            Verify DNS
          </Button>
        }
      />
      <SectionPanel title="DNS records" description="Add these records at your DNS provider">
        <ol className="mb-4 list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
          <li>Add domain</li>
          <li>Publish generated DNS records</li>
          <li>Run Verify DNS</li>
          <li>Send only after VERIFIED</li>
        </ol>
        <ul className="space-y-3">
          {(verification?.records ?? []).map((record) => (
            <li key={record.id} className="rounded-lg border border-border/70 p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-semibold">
                  {record.type} · <span className="font-mono text-xs font-normal">{record.name}</span>
                </p>
                <Badge variant={record.status === "VERIFIED" ? "success" : "secondary"}>{record.status}</Badge>
              </div>
              <div className="mt-2 flex items-start gap-2">
                <div className="min-w-0 flex-1">
                  <CodeBlock code={record.value} />
                </div>
                <CopyButton value={record.value} />
              </div>
            </li>
          ))}
        </ul>
      </SectionPanel>
    </div>
  );
}
