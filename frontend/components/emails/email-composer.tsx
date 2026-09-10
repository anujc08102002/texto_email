"use client";

import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { RecipientChips } from "@/components/emails/recipient-chips";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
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

function platformFrom(slug?: string | null) {
  if (!slug) return "";
  return `noreply@${slug}.texto.test`;
}

function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `idem_${Date.now()}_${Math.random().toString(16).slice(2)}`;
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
    Promise.all([fetchCurrentUser().catch(() => user), listDomains().catch(() => [])])
      .then(([profile, domains]) => {
        if (cancelled) return;
        const identities: string[] = [];
        const slug = profile?.tenantSlug;
        const platform = platformFrom(slug);
        if (platform) identities.push(platform);
        for (const domain of domains) {
          if (domain.status === "VERIFIED" || domain.verificationStatus === "VERIFIED") {
            const address = `noreply@${domain.domain}`;
            if (!identities.includes(address)) identities.push(address);
          }
        }
        setFromOptions(identities);
        setFrom((current) => (current.trim() ? current : identities[0] ?? platformFrom(user?.tenantSlug)));
      })
      .catch(() => {
        /* optional UX */
      });
    return () => {
      cancelled = true;
    };
  }, [user]);

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

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <PageHeader
        eyebrow="Workspace"
        title="Compose"
        description="Messages are accepted as QUEUED, then delivered asynchronously via RabbitMQ → Mailpit."
      />

      {quotaLabel ? (
        <Alert variant={quotaBlocked ? "error" : "info"}>
          <AlertDescription>
            Monthly emails: <span className="font-mono">{quotaLabel}</span>
            {quotaBlocked ? " — sending is blocked until the next period or a plan upgrade." : null}
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_280px]">
        <SectionPanel title="Message header" className="space-y-4">
          <div className="space-y-1.5">
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
            <p className="text-[11px] text-muted-foreground">
              Local sends use <span className="font-mono">noreply@{"{slug}"}.texto.test</span>, which is auto-verified
              with SPF, DKIM, and DMARC.
            </p>
          </div>
          <RecipientChips id="to" label="To" values={to} onChange={setTo} required />
          <div className="grid gap-4 sm:grid-cols-2">
            <RecipientChips id="cc" label="Cc" values={cc} onChange={setCc} />
            <RecipientChips id="bcc" label="Bcc" values={bcc} onChange={setBcc} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="replyTo">Reply-To</Label>
            <Input
              id="replyTo"
              value={replyTo}
              onChange={(event) => setReplyTo(event.target.value)}
              className="font-mono text-sm"
              placeholder="support@example.com"
            />
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="subject">Subject</Label>
              <span className="font-mono text-[11px] text-muted-foreground">{subject.length}/255</span>
            </div>
            <Input
              id="subject"
              value={subject}
              maxLength={255}
              onChange={(event) => setSubject(event.target.value)}
              required
            />
          </div>
        </SectionPanel>

        <SectionPanel title="Send summary">
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="tech-label">Recipients</dt>
              <dd className="mt-1 font-mono text-xs">{to.length + cc.length + bcc.length} units</dd>
            </div>
            <div>
              <dt className="tech-label">Quota model</dt>
              <dd className="mt-1 text-xs text-muted-foreground">1 recipient = 1 monthly email</dd>
            </div>
            <div>
              <dt className="tech-label">Delivery</dt>
              <dd className="mt-1 text-xs text-muted-foreground">Async · Mailpit local sink</dd>
            </div>
          </dl>
        </SectionPanel>
      </div>

      <SectionPanel
        title="Body"
        action={
          <Tabs value={mode} onValueChange={setMode}>
            <TabsList>
              <TabsTrigger value="text">Text</TabsTrigger>
              <TabsTrigger value="html">HTML</TabsTrigger>
            </TabsList>
          </Tabs>
        }
      >
        <Tabs value={mode} onValueChange={setMode}>
          <TabsContent value="text">
            <Textarea
              value={body}
              maxLength={20000}
              rows={16}
              onChange={(event) => setBody(event.target.value)}
              className="min-h-80"
              placeholder="Write the message…"
            />
          </TabsContent>
          <TabsContent value="html">
            <Textarea
              value={body}
              maxLength={20000}
              rows={16}
              onChange={(event) => setBody(event.target.value)}
              className="min-h-80 font-mono text-[13px]"
              placeholder="<html>…</html>"
            />
          </TabsContent>
        </Tabs>
      </SectionPanel>

      {error ? (
        <Alert variant="error">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      <div className="sticky bottom-4 z-10 flex flex-wrap items-center justify-end gap-2 rounded-lg border border-border/80 bg-card/90 p-3 shadow-sm backdrop-blur-xl">
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
