"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CodeBlock } from "@/components/ui/code-block";
import { CopyButton } from "@/components/ui/copy-button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import {
  getWebhook,
  listWebhookEvents,
  pauseWebhook,
  resumeWebhook,
  rotateWebhookSecret,
  testWebhook,
} from "@/services/webhooks";
import type { WebhookConfig, WebhookEvent } from "@/types/api";

export function WebhookDetail({ webhookId }: { webhookId: string }) {
  const [webhook, setWebhook] = useState<WebhookConfig | null>(null);
  const [events, setEvents] = useState<WebhookEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getWebhook(webhookId), listWebhookEvents(webhookId)])
      .then(([cfg, evts]) => {
        if (cancelled) return;
        setWebhook(cfg);
        setEvents(evts);
        setError(null);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load webhook.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [webhookId]);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const [cfg, evts] = await Promise.all([getWebhook(webhookId), listWebhookEvents(webhookId)]);
      setWebhook(cfg);
      setEvents(evts);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load webhook.");
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <LoadingState label="Loading webhook" />;
  if (error) return <ErrorState description={error} onRetry={reload} />;
  if (!webhook) return null;

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Developer"
        title="Webhook endpoint"
        description={webhook.url}
        actions={
          <>
            {webhook.status === "ACTIVE" ? (
              <Button
                variant="secondary"
                onClick={async () => {
                  try {
                    setWebhook(await pauseWebhook(webhook.id));
                    toast.success("Paused");
                  } catch (cause) {
                    toast.error(cause instanceof ApiClientError ? cause.message : "Pause failed");
                  }
                }}
              >
                Pause
              </Button>
            ) : (
              <Button
                variant="secondary"
                onClick={async () => {
                  try {
                    setWebhook(await resumeWebhook(webhook.id));
                    toast.success("Resumed");
                  } catch (cause) {
                    toast.error(cause instanceof ApiClientError ? cause.message : "Resume failed");
                  }
                }}
              >
                Resume
              </Button>
            )}
            <Button
              variant="ghost"
              onClick={async () => {
                try {
                  await testWebhook(webhook.id);
                  toast.success("Test event enqueued");
                  await reload();
                } catch (cause) {
                  toast.error(cause instanceof ApiClientError ? cause.message : "Test failed");
                }
              }}
            >
              Send test
            </Button>
            <Button
              variant="ghost"
              onClick={async () => {
                try {
                  const rotated = await rotateWebhookSecret(webhook.id);
                  setSecret(rotated.secret);
                  setWebhook(rotated.webhook);
                  toast.success("Secret rotated — copy now");
                } catch (cause) {
                  toast.error(cause instanceof ApiClientError ? cause.message : "Rotate failed");
                }
              }}
            >
              Rotate secret
            </Button>
          </>
        }
      />

      {secret ? (
        <Alert>
          <AlertTitle>New signing secret</AlertTitle>
          <AlertDescription>
            <div className="mt-2 flex items-center gap-2">
              <code className="flex-1 overflow-x-auto rounded bg-muted px-2 py-1 font-mono text-xs">{secret}</code>
              <CopyButton value={secret} />
            </div>
          </AlertDescription>
        </Alert>
      ) : null}

      <SectionPanel title="Configuration">
        <dl className="grid gap-3 sm:grid-cols-2 text-sm">
          <div>
            <dt className="tech-label">Status</dt>
            <dd className="mt-1">
              <Badge variant={webhook.status === "ACTIVE" ? "success" : "secondary"}>{webhook.status}</Badge>
            </dd>
          </div>
          <div>
            <dt className="tech-label">Secret prefix</dt>
            <dd className="mt-1 font-mono text-xs">{webhook.secretPrefix}…</dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="tech-label">Events</dt>
            <dd className="mt-1 font-mono text-xs text-muted-foreground">{webhook.eventTypes.join(", ")}</dd>
          </div>
        </dl>
        <p className="mt-4 text-caption">
          Signature: HMAC-SHA256 of <code className="font-mono">timestamp.raw_body</code> via headers Webhook-Id,
          Webhook-Timestamp, Webhook-Signature.
        </p>
      </SectionPanel>

      <SectionPanel title="Event history">
        {events.length === 0 ? (
          <p className="text-sm text-muted-foreground">No events yet.</p>
        ) : (
          <ul className="space-y-3">
            {events.map((event) => (
              <li key={event.id} className="rounded-lg border border-border/70 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-mono text-xs">{event.id}</p>
                  <Badge variant="secondary">{event.status}</Badge>
                </div>
                <p className="mt-1 text-sm">
                  {event.eventType} · attempts {event.attemptCount}
                  {event.lastResponseCode != null ? ` · HTTP ${event.lastResponseCode}` : ""}
                </p>
                <p className="text-caption">{formatDateTime(event.createdAt)}</p>
                {event.lastError ? <p className="mt-1 text-xs text-destructive">{event.lastError}</p> : null}
                <div className="mt-2">
                  <CodeBlock code={JSON.stringify(event.payload, null, 2)} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </SectionPanel>
    </div>
  );
}
