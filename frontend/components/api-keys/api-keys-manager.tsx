"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Check, Copy, KeyRound } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import { HealthIndicator } from "@/components/ops/health-indicator";
import { SectionPanel } from "@/components/ops/section-panel";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { createApiKey, listApiKeys, revokeApiKey } from "@/services/api-keys";
import type { ApiKey } from "@/types/api";
import { cn } from "@/lib/utils";

export function ApiKeysManager() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [environment, setEnvironment] = useState<"TEST" | "LIVE">("TEST");
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [creating, setCreating] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listApiKeys()
      .then((data) => {
        if (!cancelled) {
          setKeys(data);
          setError(null);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load API keys.");
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
      setKeys(await listApiKeys());
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load API keys.");
    } finally {
      setLoading(false);
    }
  }

  async function onCreate() {
    setCreating(true);
    try {
      const created = await createApiKey({ name: name.trim(), environment });
      setRevealedSecret(created.secret);
      setConfirmed(false);
      toast.success("API key created — copy the secret now");
      await reload();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to create API key.");
    } finally {
      setCreating(false);
    }
  }

  async function copySecret() {
    if (!revealedSecret) return;
    await navigator.clipboard.writeText(revealedSecret);
    toast.success("Secret copied");
  }

  async function onRevoke(id: string) {
    setRevokingId(id);
    try {
      await revokeApiKey(id);
      toast.success("API key revoked");
      await reload();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to revoke key.");
    } finally {
      setRevokingId(null);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="tech-label">Developer access</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Secrets are shown once at creation. List endpoints only return prefixes.
          </p>
        </div>
        <Dialog
          open={open}
          onOpenChange={(next) => {
            setOpen(next);
            if (!next) {
              setRevealedSecret(null);
              setName("");
              setConfirmed(false);
            }
          }}
        >
          <DialogTrigger asChild>
            <Button>Create API key</Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>Create API key</DialogTitle>
              <DialogDescription>
                Reveal → copy → confirm stored securely. The secret is never returned again.
              </DialogDescription>
            </DialogHeader>

            {!revealedSecret ? (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <Label htmlFor="key-name">Name</Label>
                  <Input
                    id="key-name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    placeholder="Production sending"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>Environment</Label>
                  <Select value={environment} onValueChange={(value) => setEnvironment(value as "TEST" | "LIVE")}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="LIVE">Live</SelectItem>
                      <SelectItem value="TEST">Test</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <Alert variant="warning">
                  <AlertTitle>Store this secret now</AlertTitle>
                  <AlertDescription>It will never be shown again after you close this dialog.</AlertDescription>
                </Alert>
                <div className="rounded-xl border border-border/80 bg-muted/30 p-3">
                  <p className="tech-label">Secret</p>
                  <code className="mt-2 block break-all font-mono text-xs">{revealedSecret}</code>
                  <Button type="button" size="sm" variant="secondary" className="mt-3" onClick={copySecret}>
                    <Copy />
                    Copy
                  </Button>
                </div>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="mt-1"
                    checked={confirmed}
                    onChange={(event) => setConfirmed(event.target.checked)}
                  />
                  <span>I have stored this secret securely and understand it cannot be recovered.</span>
                </label>
              </div>
            )}

            <DialogFooter>
              {!revealedSecret ? (
                <Button type="button" onClick={onCreate} disabled={!name.trim()} loading={creating}>
                  Create
                </Button>
              ) : (
                <Button type="button" variant="secondary" disabled={!confirmed} onClick={() => setOpen(false)}>
                  <Check />
                  Done
                </Button>
              )}
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {loading ? <LoadingState label="Loading API keys" /> : null}
      {error ? <ErrorState description={error} onRetry={() => void reload()} /> : null}

      {!loading && !error && keys.length === 0 ? (
        <EmptyState
          icon={<KeyRound className="size-5" />}
          title="Create an API key to start sending through the API"
          description="Keys are tenant-scoped and hashed at rest. Full secrets are never listed."
        />
      ) : null}

      {!loading && !error && keys.length > 0 ? (
        <SectionPanel title="Issued keys" description="Prefixes only — full secrets are never re-displayed">
          <div className="space-y-2">
            {keys.map((key) => (
              <div
                key={key.id}
                className={cn(
                  "grid gap-3 rounded-xl border border-border/70 bg-card/70 px-4 py-3 lg:grid-cols-[1.2fr_0.6fr_1fr_auto]",
                )}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <HealthIndicator tone={key.status === "ACTIVE" ? "operational" : "unknown"} />
                    <p className="text-sm font-medium">{key.name}</p>
                  </div>
                  <p className="mt-1 font-mono text-xs text-muted-foreground">{key.keyPrefix}••••••••••••</p>
                </div>
                <div>
                  <p className="tech-label">Environment</p>
                  <Badge variant={key.environment === "LIVE" ? "success" : "secondary"} className="mt-1 uppercase">
                    {key.environment}
                  </Badge>
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <p className="tech-label">Created</p>
                    <p className="mt-1 font-mono">{formatDateTime(key.createdAt)}</p>
                  </div>
                  <div>
                    <p className="tech-label">Last used</p>
                    <p className="mt-1 font-mono">{key.lastUsedAt ? formatDateTime(key.lastUsedAt) : "Never"}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={key.status === "ACTIVE" ? "success" : "secondary"}>{key.status}</Badge>
                  {key.status === "ACTIVE" ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      loading={revokingId === key.id}
                      onClick={() => void onRevoke(key.id)}
                    >
                      Revoke
                    </Button>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </SectionPanel>
      ) : null}
    </div>
  );
}
