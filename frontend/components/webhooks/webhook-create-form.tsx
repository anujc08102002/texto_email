"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Check, Copy } from "lucide-react";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import { createWebhook, WEBHOOK_EVENT_TYPES } from "@/services/webhooks";
import { cn } from "@/lib/utils";

export function WebhookCreateForm() {
  const router = useRouter();
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [eventTypes, setEventTypes] = useState<string[]>(["email.delivered", "email.bounced", "email.failed"]);
  const [submitting, setSubmitting] = useState(false);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  function toggleEvent(type: string) {
    setEventTypes((prev) => (prev.includes(type) ? prev.filter((item) => item !== type) : [...prev, type]));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (eventTypes.length === 0) {
      toast.error("Select at least one event type.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createWebhook({
        url: url.trim(),
        description: description.trim() || undefined,
        eventTypes,
      });
      setRevealedSecret(created.secret);
      setCreatedId(created.webhook.id);
      toast.success("Webhook created — copy the signing secret now");
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to create webhook.");
    } finally {
      setSubmitting(false);
    }
  }

  async function copySecret() {
    if (!revealedSecret) return;
    await navigator.clipboard.writeText(revealedSecret);
    toast.success("Secret copied");
  }

  if (revealedSecret && createdId) {
    return (
      <SectionPanel title="Signing secret" description="Shown once — store it securely">
        <Alert variant="warning">
          <AlertTitle>Store this secret now</AlertTitle>
          <AlertDescription>It will never be shown again after you leave this page.</AlertDescription>
        </Alert>
        <div className="mt-4 rounded-xl border border-border/80 bg-muted/30 p-3">
          <p className="tech-label">Secret</p>
          <code className="mt-2 block break-all font-mono text-xs">{revealedSecret}</code>
          <Button type="button" size="sm" variant="secondary" className="mt-3" onClick={() => void copySecret()}>
            <Copy />
            Copy
          </Button>
        </div>
        <label className="mt-4 flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-1"
            checked={confirmed}
            onChange={(event) => setConfirmed(event.target.checked)}
          />
          <span>I have stored this secret securely and understand it cannot be recovered.</span>
        </label>
        <div className="mt-4 flex justify-end">
          <Button
            type="button"
            variant="secondary"
            disabled={!confirmed}
            onClick={() => router.push(`/webhooks/${createdId}`)}
          >
            <Check />
            Continue to webhook
          </Button>
        </div>
      </SectionPanel>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <SectionPanel title="Endpoint" description="HTTPS destination for signed event payloads">
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="webhook-url">URL</Label>
            <Input
              id="webhook-url"
              type="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://example.com/webhooks/texto"
              className="font-mono text-sm"
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="webhook-description">Description</Label>
            <Textarea
              id="webhook-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Optional"
            />
          </div>
        </div>
      </SectionPanel>

      <SectionPanel title="Event types" description="Subscribe to delivery lifecycle events">
        <div className="grid gap-2 sm:grid-cols-2">
          {WEBHOOK_EVENT_TYPES.map((type) => {
            const checked = eventTypes.includes(type);
            return (
              <button
                key={type}
                type="button"
                onClick={() => toggleEvent(type)}
                className={cn(
                  "rounded-xl border px-3 py-2 text-left font-mono text-xs transition-colors",
                  checked ? "border-primary/40 bg-primary/5" : "border-border/70 bg-card/60 hover:bg-card",
                )}
              >
                {type}
              </button>
            );
          })}
        </div>
      </SectionPanel>

      <div className="flex justify-end gap-2">
        <Button asChild type="button" variant="secondary">
          <Link href="/webhooks">Cancel</Link>
        </Button>
        <Button type="submit" loading={submitting} disabled={!url.trim() || eventTypes.length === 0}>
          Create webhook
        </Button>
      </div>
    </form>
  );
}
