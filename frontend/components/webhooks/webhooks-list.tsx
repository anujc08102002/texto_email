"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Webhook } from "lucide-react";
import { UpgradeHint } from "@/components/billing/upgrade-hint";
import { HealthIndicator } from "@/components/ops/health-indicator";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { getEntitlements } from "@/services/platform";
import { listWebhooks } from "@/services/webhooks";
import type { WebhookConfig } from "@/types/api";

function statusTone(status: string) {
  if (status === "ACTIVE") return "operational" as const;
  if (status === "PAUSED") return "warning" as const;
  return "unknown" as const;
}

export function WebhooksList() {
  const [webhooks, setWebhooks] = useState<WebhookConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [allowed, setAllowed] = useState(true);

  function reload() {
    setLoading(true);
    setError(null);
    Promise.all([listWebhooks(), getEntitlements().catch(() => null)])
      .then(([data, entitlements]) => {
        setWebhooks(data);
        if (entitlements) {
          setAllowed(entitlements.features.WEBHOOKS !== false);
        }
      })
      .catch((cause) => {
        setError(cause instanceof ApiClientError ? cause.message : "Unable to load webhooks.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    let cancelled = false;
    Promise.all([listWebhooks(), getEntitlements().catch(() => null)])
      .then(([data, entitlements]) => {
        if (cancelled) return;
        setWebhooks(data);
        if (entitlements) {
          setAllowed(entitlements.features.WEBHOOKS !== false);
        }
        setError(null);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load webhooks.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex min-h-full flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-border/60 pb-5">
        <div>
          <p className="tech-label text-primary">Developer</p>
          <h1 className="mt-1 text-page-heading text-foreground">Webhooks</h1>
          <p className="text-body mt-1 max-w-2xl text-muted-foreground">
            HTTPS destinations for delivery, bounce, and complaint events.
          </p>
        </div>
        {allowed ? (
          <Button asChild>
            <Link href="/webhooks/new">Add endpoint</Link>
          </Button>
        ) : (
          <Button asChild variant="secondary">
            <Link href="/billing">Upgrade to add</Link>
          </Button>
        )}
      </div>

      {!allowed ? <UpgradeHint feature="Webhooks" /> : null}

      <SectionPanel title="Endpoints" description="Signed payloads · retry policy · delivery logs">
        {loading ? <LoadingState label="Loading webhooks" /> : null}
        {error ? <ErrorState description={error} onRetry={reload} /> : null}
        {!loading && !error && webhooks.length === 0 ? (
          <EmptyState
            icon={<Webhook className="size-5" />}
            title="Add a webhook endpoint"
            description="Receive signed event payloads for delivery lifecycle changes."
            action={
              allowed ? (
                <Button asChild>
                  <Link href="/webhooks/new">Add endpoint</Link>
                </Button>
              ) : undefined
            }
          />
        ) : null}
        {!loading && !error && webhooks.length > 0 ? (
          <div className="space-y-2">
            {webhooks.map((hook) => (
              <Link
                key={hook.id}
                href={`/webhooks/${hook.id}`}
                className="block rounded-xl border border-border/70 bg-card/70 px-4 py-3 transition-colors hover:border-border hover:bg-card"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <HealthIndicator tone={statusTone(hook.status)} />
                      <p className="truncate font-mono text-sm font-medium">{hook.url}</p>
                    </div>
                    {hook.description ? (
                      <p className="mt-1 text-sm text-muted-foreground">{hook.description}</p>
                    ) : null}
                    <p className="mt-2 font-mono text-[11px] text-muted-foreground">
                      Secret {hook.secretPrefix}•••• · Updated {formatDateTime(hook.updatedAt)}
                    </p>
                  </div>
                  <Badge
                    variant={
                      hook.status === "ACTIVE" ? "success" : hook.status === "PAUSED" ? "warning" : "secondary"
                    }
                  >
                    {hook.status}
                  </Badge>
                </div>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {hook.eventTypes.map((eventType) => (
                    <Badge key={eventType} variant="outline" className="font-mono text-[10px] normal-case">
                      {eventType}
                    </Badge>
                  ))}
                </div>
              </Link>
            ))}
          </div>
        ) : null}
      </SectionPanel>
    </div>
  );
}
