"use client";

import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { RecipientChips } from "@/components/emails/recipient-chips";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { HtmlPreview } from "@/components/templates/html-preview";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import { useStoredUser } from "@/hooks/use-stored-user";
import { fetchCurrentUser } from "@/services/auth";
import { listDomains } from "@/services/domains";
import { getUsage } from "@/services/platform";
import { sendEmail } from "@/services/emails";

function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `idem_${Date.now()}_${Math.random().toString(16).slice(2)}`;
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

export function EmailComposer({ defaultTo = "" }: { defaultTo?: string }) {
  const router = useRouter();
  const user = useStoredUser();
  const [from, setFrom] = useState("");
  const [fromOptions, setFromOptions] = useState<string[]>([]);
  const [to, setTo] = useState<string[]>(defaultTo ? [defaultTo] : []);
  const [cc, setCc] = useState<string[]>([]);
  const [bcc, setBcc] = useState<string[]>([]);
  const [replyTo, setReplyTo] = useState("");
  const [subject, setSubject] = useState("");
  const [mode, setMode] = useState("text");
  const [body, setBody] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [quotaLabel, setQuotaLabel] = useState<string | null>(null);
  const [quotaBlocked, setQuotaBlocked] = useState(false);

  const canSend =
    from.trim().length > 0 &&
    to.length > 0 &&
    subject.trim().length > 0 &&
    body.trim().length > 0 &&
    !quotaBlocked;

  useEffect(() => {
    let cancelled = false;

    listDomains()
      .catch(() => [])
      .then((domains) => {
        if (cancelled) return;

        const identities: string[] = [];

        for (const domain of domains) {
          if (domain.status === "VERIFIED" || domain.verificationStatus === "VERIFIED") {
            const address = `noreply@${domain.domain}`;

            if (!identities.includes(address)) {
              identities.push(address);
            }
          }
        }

        setFromOptions(identities);

        setFrom((current) => {
          if (current.trim()) {
            return current;
          }

          return identities[0] ?? "";
        });
      })
      .catch(() => {
        if (!cancelled) {
          setFromOptions([]);
          setFrom("");
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    getUsage()
      .then((usage) => {
        const metric = usage.metrics.find((item) => item.metric === "MONTHLY_EMAILS");
        if (!metric) return;
        const limit = metric.limit == null ? "∞" : String(metric.limit);
        setQuotaLabel(`${metric.used} / ${limit}`);
        setQuotaBlocked(metric.remaining != null && metric.remaining <= 0);
      })
      .catch(() => {
        /* optional UX */
      });
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canSend) {
      setError(quotaBlocked ? "Monthly email quota is exhausted." : "From, recipients, subject, and body are required.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const queued = await sendEmail(
        {
          from: from.trim(),
          to,
          cc: cc.length ? cc : undefined,
          bcc: bcc.length ? bcc : undefined,
          replyTo: replyTo.trim() || undefined,
          subject: subject.trim(),
          text: mode === "text" ? body : undefined,
          html: mode === "html" ? body : undefined,
        },
        newIdempotencyKey(),
      );
      toast.success(`Queued · ${queued.status}`);
      router.push(`/emails/${queued.id}`);
    } catch (cause) {
      const message = cause instanceof ApiClientError ? cause.message : "Unable to queue the email.";
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  }

  const previewHtml =
    mode === "html"
      ? body || "<p style='color:#888'>HTML preview appears here.</p>"
      : `<pre style="font:14px/1.6 ui-sans-serif,system-ui;white-space:pre-wrap;padding:16px;margin:0">${escapeHtml(body) || "Plain-text preview appears here."}</pre>`;

  return (
    <form onSubmit={onSubmit} className="flex min-h-full flex-col gap-4 pb-28 lg:pb-6">
      <PageHeader
        eyebrow="Workspace"
        title="Compose"
        description="Messages are accepted as QUEUED, then delivered asynchronously through the configured email delivery provider."
      />

      {quotaLabel ? (
        <Alert variant={quotaBlocked ? "error" : "info"}>
          <AlertDescription>
            Monthly emails: <span className="font-mono">{quotaLabel}</span>
            {quotaBlocked ? " — sending is blocked until the next period or a plan upgrade." : null}
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="grid flex-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(240px,320px)]">
        <SectionPanel className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="from">From</Label>
              <Input
                id="from"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                list="from-identities"
                placeholder="noreply@your-domain.com"
                className="font-mono text-sm"
                required
              />
              {fromOptions.length > 0 ? (
                <datalist id="from-identities">
                  {fromOptions.map((address) => (
                    <option key={address} value={address} />
                  ))}
                </datalist>
              ) : null}
            </div>
            <div className="sm:col-span-2">
              <RecipientChips id="to" label="To" values={to} onChange={setTo} required />
            </div>
            <RecipientChips id="cc" label="Cc" values={cc} onChange={setCc} />
            <RecipientChips id="bcc" label="Bcc" values={bcc} onChange={setBcc} />
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="replyTo">Reply-To</Label>
              <Input
                id="replyTo"
                value={replyTo}
                onChange={(event) => setReplyTo(event.target.value)}
                className="font-mono text-sm"
                placeholder="support@example.com"
              />
            </div>
            <div className="space-y-1.5 sm:col-span-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="subject">Subject</Label>
                <span className="font-mono text-[11px] text-muted-foreground">{subject.length}/255</span>
              </div>
              <Input
                id="subject"
                value={subject}
                maxLength={255}
                onChange={(event) => setSubject(event.target.value)}
                className="h-11 text-base"
                required
              />
            </div>
          </div>

          <Tabs value={mode} onValueChange={setMode}>
            <div className="flex items-center justify-between">
              <p className="text-sm font-medium">Body</p>
              <TabsList>
                <TabsTrigger value="text">Text</TabsTrigger>
                <TabsTrigger value="html">HTML</TabsTrigger>
              </TabsList>
            </div>
            <TabsContent value="text">
              <Textarea
                value={body}
                maxLength={20000}
                rows={16}
                onChange={(event) => setBody(event.target.value)}
                className="min-h-80 rounded-2xl"
                placeholder="Write the message…"
              />
            </TabsContent>
            <TabsContent value="html">
              <Textarea
                value={body}
                maxLength={20000}
                rows={16}
                onChange={(event) => setBody(event.target.value)}
                className="min-h-80 rounded-2xl font-mono text-[13px]"
                placeholder="<html>…</html>"
              />
            </TabsContent>
          </Tabs>
        </SectionPanel>

        <div className="space-y-4">
          <SectionPanel title="Preview" elevated>
            <p className="mb-3 truncate text-sm font-medium">{subject || "Untitled message"}</p>
            <HtmlPreview html={previewHtml} title="Message preview" className="h-56 w-full rounded-xl border border-border/70 bg-white sm:h-72" />
          </SectionPanel>
          <SectionPanel title="Send summary">
            <dl className="space-y-3 text-sm">
              <div>
                <dt className="tech-label">Recipients</dt>
                <dd className="mt-1 text-sm">{to.length + cc.length + bcc.length} units</dd>
              </div>
              <div>
                <dt className="tech-label">Quota</dt>
                <dd className="mt-1 font-mono text-xs">{quotaLabel ?? "—"}</dd>
              </div>
              <div>
                <dt className="tech-label">Delivery</dt>
                <dd className="mt-1 text-xs text-muted-foreground">
                  Async · Configured delivery provider
                </dd>
              </div>
            </dl>
          </SectionPanel>
        </div>
      </div>

      {error ? (
        <Alert variant="error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      <div className="sticky bottom-3 z-10 flex flex-wrap items-center justify-end gap-2 rounded-2xl border border-border/80 bg-card/95 p-3 shadow-md backdrop-blur-xl sm:gap-3 [padding-bottom:max(0.75rem,env(safe-area-inset-bottom))]">
        <Button type="button" variant="secondary" asChild>
          <Link href="/emails">Cancel</Link>
        </Button>
        <Button type="submit" loading={submitting} disabled={!canSend}>
          Send
        </Button>
      </div>
    </form>
  );
}
