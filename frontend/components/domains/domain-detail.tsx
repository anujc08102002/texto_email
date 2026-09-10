"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { DomainAuthStatus } from "@/components/domains/domain-auth-status";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CodeBlock } from "@/components/ui/code-block";
import { CopyButton } from "@/components/ui/copy-button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  deleteDomain,
  getDomain,
  getDomainVerification,
  verifyDomain,
} from "@/services/domains";
import type { Domain, DomainVerification } from "@/types/api";

function Step({
  index,
  title,
  done,
  active,
  children,
}: {
  index: number;
  title: string;
  done?: boolean;
  active?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "rounded-xl border px-4 py-4",
        active ? "border-primary/40 bg-primary/5" : "border-border/70 bg-card/60",
      )}
    >
      <div className="mb-3 flex items-center gap-2">
        <span className="flex size-6 items-center justify-center rounded-full border border-border font-mono text-[11px]">
          {done ? "✓" : index}
        </span>
        <h3 className="text-sm font-semibold">{title}</h3>
      </div>
      {children}
    </div>
  );
}

export function DomainDetail({ domainId }: { domainId: string }) {
  const router = useRouter();
  const [domain, setDomain] = useState<Domain | null>(null);
  const [verification, setVerification] = useState<DomainVerification | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getDomain(domainId), getDomainVerification(domainId)])
      .then(([dom, ver]) => {
        if (cancelled) return;
        setDomain(dom);
        setVerification(ver);
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
  }, [domainId, reloadKey]);

  async function onVerify() {
    setVerifying(true);
    try {
      const updated = await verifyDomain(domainId);
      setDomain(updated);
      setVerification(await getDomainVerification(domainId));
      toast.success(
        updated.verificationStatus?.toLowerCase() === "verified"
          ? "Domain verified"
          : "Verification ran — check DNS record status below",
      );
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to verify domain.");
    } finally {
      setVerifying(false);
    }
  }

  async function onDelete() {
    if (!window.confirm("Delete this sending domain? This cannot be undone.")) return;
    setDeleting(true);
    try {
      await deleteDomain(domainId);
      toast.success("Domain deleted");
      router.push("/domains");
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to delete domain.");
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <LoadingState label="Loading domain" />;
  if (error) {
    return (
      <ErrorState
        description={error}
        onRetry={() => {
          setLoading(true);
          setError(null);
          setReloadKey((key) => key + 1);
        }}
      />
    );
  }
  if (!domain) return null;

  const verified = domain.verificationStatus?.toLowerCase() === "verified";
  const hasRecords = (verification?.records.length ?? 0) > 0;

  return (
    <div className="space-y-4">
      <SectionPanel
        title={domain.domain}
        description={`Registered ${formatDateTime(domain.createdAt)}`}
        action={
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" loading={verifying} onClick={() => void onVerify()}>
              Verify DNS
            </Button>
            <Button type="button" size="sm" variant="destructive" loading={deleting} onClick={() => void onDelete()}>
              Delete
            </Button>
          </div>
        }
      >
        <div className="mt-4">
          <DomainAuthStatus domain={domain} verification={verification} />
        </div>
      </SectionPanel>

      <div className="grid gap-4 lg:grid-cols-3">
        <Step index={1} title="Register domain" done>
          <p className="text-sm text-muted-foreground">
            Domain is registered in this workspace. Continue by publishing DNS records at your registrar.
          </p>
        </Step>
        <Step index={2} title="Publish DNS records" done={hasRecords} active={!verified}>
          <p className="text-sm text-muted-foreground">
            Copy each record below into your DNS provider. Propagation can take several minutes.
          </p>
        </Step>
        <Step index={3} title="Verify" done={verified} active={hasRecords && !verified}>
          <p className="mb-3 text-sm text-muted-foreground">
            After DNS propagates, run verification to confirm ownership, SPF, DKIM, and DMARC.
          </p>
          <Button type="button" size="sm" loading={verifying} onClick={() => void onVerify()}>
            Run verification
          </Button>
        </Step>
      </div>

      <SectionPanel title="DNS records" description="Never includes private keys">
        {!verification || verification.records.length === 0 ? (
          <p className="text-sm text-muted-foreground">No DNS records available yet.</p>
        ) : (
          <div className="space-y-3">
            {verification.records.map((record) => (
              <div key={record.id} className="rounded-xl border border-border/70 bg-muted/20 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="outline" className="font-mono text-[10px]">
                      {record.type}
                    </Badge>
                    <span className="font-mono text-xs">{record.name}</span>
                    <Badge variant="secondary">{record.status}</Badge>
                  </div>
                  {record.detail ? <p className="mt-1 text-xs text-muted-foreground">{record.detail}</p> : null}
                  <CopyButton value={record.value} label="Copy DNS value" />
                </div>
                <CodeBlock code={record.value} className="mt-2" copyable={false} />
                <div className="mt-2 flex flex-wrap gap-3 font-mono text-[10px] text-muted-foreground">
                  {record.selector ? <span>Selector {record.selector}</span> : null}
                  {record.verifiedAt ? <span>Verified {formatDateTime(record.verifiedAt)}</span> : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </SectionPanel>

      <Button asChild variant="secondary">
        <Link href="/domains">Back to domains</Link>
      </Button>
    </div>
  );
}
