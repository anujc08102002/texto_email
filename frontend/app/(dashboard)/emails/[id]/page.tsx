"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { DeliveryTimeline } from "@/components/emails/delivery-timeline";
import { EmailStatusBadge } from "@/components/emails/email-status-badge";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { CodeBlock } from "@/components/ui/code-block";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { formatDateTime } from "@/lib/format";
import { ApiClientError } from "@/lib/api";
import { getEmail } from "@/services/emails";
import type { EmailMessageDetail } from "@/types/api";

function Field({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <p className="tech-label">{label}</p>
      <p className={`mt-1 break-all text-sm ${mono ? "font-mono text-xs" : ""}`}>{value}</p>
    </div>
  );
}

export default function EmailDetailPage() {
  const params = useParams<{ id: string }>();
  const [message, setMessage] = useState<EmailMessageDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getEmail(params.id)
      .then((result) => {
        if (!cancelled) {
          setMessage(result);
          setError(null);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load this message.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [params.id]);

  if (loading) return <LoadingState rows={6} label="Loading message" />;

  if (error || !message) {
    return (
      <div className="space-y-4">
        <ErrorState description={error ?? "Message not found."} />
        <Button asChild variant="secondary">
          <Link href="/emails">Back to emails</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Workspace"
        title={message.subject}
        description={`Status ${message.status}`}
        actions={
          <>
            <EmailStatusBadge status={message.status} />
            <Button asChild variant="secondary" size="sm">
              <Link href="/emails">Back</Link>
            </Button>
          </>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <SectionPanel title="Message">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Message ID" value={message.id} mono />
            <Field label="From" value={message.fromAddress ?? "—"} mono />
            <Field label="To" value={(message.recipientsTo ?? [message.recipient]).join(", ")} mono />
            <Field label="Cc" value={(message.recipientsCc ?? []).join(", ") || "—"} mono />
            <Field label="Bcc" value={(message.recipientsBcc ?? []).join(", ") || "—"} mono />
            <Field label="Reply-To" value={message.replyTo ?? "—"} mono />
            <Field label="Created" value={formatDateTime(message.createdAt)} />
            <Field label="Queued" value={message.queuedAt ? formatDateTime(message.queuedAt) : "—"} />
            <Field label="Processing" value={message.processingAt ? formatDateTime(message.processingAt) : "—"} />
            <Field label="Sending" value={message.sendingAt ? formatDateTime(message.sendingAt) : "—"} />
            <Field label="Delivered" value={message.deliveredAt ? formatDateTime(message.deliveredAt) : "—"} />
            <Field label="Attempts" value={`${message.attemptCount ?? 0} / ${message.maxAttempts ?? "—"}`} />
          </div>
          {message.lastError ? (
            <div className="mt-4">
              <p className="tech-label">Last error</p>
              <p className="mt-1 text-sm text-destructive">{message.lastError}</p>
            </div>
          ) : null}
        </SectionPanel>

        <SectionPanel title="Delivery timeline">
          <DeliveryTimeline status={message.status} />
        </SectionPanel>
      </div>

      <SectionPanel title="Delivery attempts">
        {(message.attempts ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground">No attempts recorded yet — worker may still be processing.</p>
        ) : (
          <ul className="space-y-3">
            {message.attempts!.map((attempt) => (
              <li key={attempt.id} className="rounded-lg border border-border/70 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium">Attempt {attempt.attemptNumber}</p>
                  <EmailStatusBadge status={attempt.status} />
                </div>
                <p className="mt-1 text-caption">
                  {formatDateTime(attempt.startedAt)}
                  {attempt.completedAt ? ` → ${formatDateTime(attempt.completedAt)}` : ""}
                </p>
                {attempt.errorMessage ? <p className="mt-1 text-xs text-destructive">{attempt.errorMessage}</p> : null}
                {attempt.providerResponse ? (
                  <div className="mt-2">
                    <CodeBlock code={attempt.providerResponse} />
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </SectionPanel>
    </div>
  );
}
