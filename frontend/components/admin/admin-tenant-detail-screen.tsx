"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  Ban,
  CreditCard,
  KeyRound,
  Lock,
  LogOut,
  Mail,
  NotebookPen,
  PauseCircle,
  PlayCircle,
  RefreshCw,
  ShieldAlert,
  Snowflake,
  UserRoundSearch,
} from "lucide-react";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { TenantStatusBadge } from "@/components/admin/tenant-status-badge";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { UsageMeter } from "@/components/ops/usage-meter";
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
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { previewAdminAction } from "@/lib/admin/actions";
import { formatDateTime } from "@/lib/format";
import type { AdminTenant } from "@/types/admin";

type ConfirmState = {
  title: string;
  description: string;
  action: string;
  tone?: "default" | "danger";
} | null;

function ControlButton({
  icon: Icon,
  label,
  description,
  onClick,
  tone = "default",
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  description: string;
  onClick: () => void;
  tone?: "default" | "danger" | "warning";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-start gap-3 rounded-xl border px-3 py-3 text-left transition-all duration-150 hover:shadow-sm ${
        tone === "danger"
          ? "border-destructive/30 bg-destructive/5 hover:border-destructive/50"
          : tone === "warning"
            ? "border-warning/30 bg-warning/5 hover:border-warning/50"
            : "border-border/70 bg-card/50 hover:border-primary/30 hover:bg-primary/5"
      }`}
    >
      <Icon className={`mt-0.5 size-4 shrink-0 ${tone === "danger" ? "text-destructive" : tone === "warning" ? "text-warning-foreground" : "text-primary"}`} />
      <span>
        <span className="block text-sm font-medium">{label}</span>
        <span className="mt-0.5 block text-xs text-muted-foreground">{description}</span>
      </span>
    </button>
  );
}

export function AdminTenantDetailScreen({ tenant }: { tenant: AdminTenant }) {
  const [confirm, setConfirm] = useState<ConfirmState>(null);
  const [note, setNote] = useState(tenant.notes ?? "");
  const [quotaBoost, setQuotaBoost] = useState("");
  const [credit, setCredit] = useState("");
  const [features, setFeatures] = useState(tenant.features);
  const [messageSubject, setMessageSubject] = useState("");
  const [messageBody, setMessageBody] = useState("");

  const statusLabel = useMemo(() => tenant.status.replaceAll("_", " "), [tenant.status]);

  function runConfirmed() {
    if (!confirm) return;
    previewAdminAction(confirm.action, tenant.organization);
    setConfirm(null);
  }

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Tenant control"
        title={tenant.organization}
        description={`${tenant.slug} · ${tenant.ownerEmail}`}
        actions={
          <Button asChild variant="secondary" size="sm">
            <Link href="/admin/tenants">Back to tenants</Link>
          </Button>
        }
      />
      <AdminPreviewBanner />

      <div className="flex flex-wrap items-center gap-2">
        <TenantStatusBadge status={tenant.status} />
        <Badge variant={tenant.risk === "high" ? "error" : tenant.risk === "medium" ? "warning" : "success"}>
          {tenant.risk} risk
        </Badge>
        <Badge variant="outline">{tenant.planName}</Badge>
        <span className="font-mono text-[11px] text-muted-foreground">{tenant.id}</span>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
        <SectionPanel title="Workspace profile" description="Identity, plan, and activity">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <p className="tech-label">Owner</p>
              <p className="mt-1 font-mono text-sm">{tenant.ownerEmail}</p>
            </div>
            <div>
              <p className="tech-label">Status</p>
              <p className="mt-1 text-sm capitalize">{statusLabel}</p>
            </div>
            <div>
              <p className="tech-label">Created</p>
              <p className="mt-1 font-mono text-xs">{formatDateTime(tenant.createdAt)}</p>
            </div>
            <div>
              <p className="tech-label">Last active</p>
              <p className="mt-1 font-mono text-xs">{formatDateTime(tenant.lastActiveAt)}</p>
            </div>
            <div>
              <p className="tech-label">Users</p>
              <p className="mt-1 font-mono text-sm">{tenant.users}</p>
            </div>
            <div>
              <p className="tech-label">Domains</p>
              <p className="mt-1 font-mono text-sm">{tenant.domains}</p>
            </div>
            <div>
              <p className="tech-label">Bounce rate</p>
              <p className="mt-1 font-mono text-sm">{tenant.bounceRate}%</p>
            </div>
            <div>
              <p className="tech-label">Complaint rate</p>
              <p className="mt-1 font-mono text-sm">{tenant.complaintRate}%</p>
            </div>
          </div>
          <div className="mt-5">
            <UsageMeter
              label="Messages (30d)"
              used={tenant.messages30d}
              limit={tenant.limits.messages}
              footer={`MRR $${tenant.mrr} · entitlement preview`}
            />
          </div>
        </SectionPanel>

        <SectionPanel title="Account controls" description="Suspend, freeze, reinstate, sessions">
          <div className="space-y-2">
            <ControlButton
              icon={Ban}
              label={tenant.status === "suspended" ? "Reinstate account" : "Temporarily suspend account"}
              description="Blocks login and API access until reinstated"
              tone="danger"
              onClick={() =>
                setConfirm({
                  title: tenant.status === "suspended" ? "Reinstate account?" : "Temporarily suspend account?",
                  description: "Every user in this tenant loses access until an admin reinstates the workspace.",
                  action: tenant.status === "suspended" ? "tenant.reinstate" : "tenant.suspend",
                  tone: "danger",
                })
              }
            />
            <ControlButton
              icon={Snowflake}
              label={tenant.sendingFrozen ? "Unfreeze sending" : "Freeze outbound sending"}
              description="Allow dashboard login but block all email egress"
              tone="warning"
              onClick={() =>
                setConfirm({
                  title: tenant.sendingFrozen ? "Unfreeze sending?" : "Freeze sending?",
                  description: "SMTP/API sends will be rejected while frozen.",
                  action: tenant.sendingFrozen ? "sending.unfreeze" : "sending.freeze",
                  tone: "danger",
                })
              }
            />
            <ControlButton
              icon={LogOut}
              label="Force logout all sessions"
              description="Invalidate JWTs and refresh tokens for the tenant"
              onClick={() =>
                setConfirm({
                  title: "Force logout?",
                  description: "All active sessions for this tenant will be revoked.",
                  action: "sessions.revoke_all",
                })
              }
            />
            <ControlButton
              icon={KeyRound}
              label="Revoke all API keys"
              description="Immediately disable programmatic sending credentials"
              tone="danger"
              onClick={() =>
                setConfirm({
                  title: "Revoke all API keys?",
                  description: "This cannot be undone. Keys must be reissued by the tenant.",
                  action: "api_keys.revoke_all",
                  tone: "danger",
                })
              }
            />
            <ControlButton
              icon={UserRoundSearch}
              label="Support impersonation"
              description="Read-only support session with full audit trail"
              tone="warning"
              onClick={() =>
                setConfirm({
                  title: "Start support impersonation?",
                  description: "Preview only. Impersonation must be time-boxed and fully audited.",
                  action: "support.impersonate",
                  tone: "danger",
                })
              }
            />
            <ControlButton
              icon={Ban}
              label="Permanently delete account"
              description="Irreversible removal of the tenant, users, keys, and data"
              tone="danger"
              onClick={() =>
                setConfirm({
                  title: "Permanently delete this tenant?",
                  description:
                    "This closes the account forever. Prefer temporary suspension unless legal/compliance requires deletion.",
                  action: "tenant.delete_permanent",
                  tone: "danger",
                })
              }
            />
          </div>
        </SectionPanel>
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <SectionPanel title="Billing controls" description="Credits, plan, reminders">
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="credit">Apply credit (USD)</Label>
              <Input id="credit" value={credit} onChange={(e) => setCredit(e.target.value)} placeholder="50.00" className="font-mono" />
            </div>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("billing.apply_credit", `${tenant.organization} · $${credit || "0"}`)}
            >
              <CreditCard />
              Apply credit
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("billing.send_reminder", tenant.organization)}
            >
              <Mail />
              Send payment reminder
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("billing.change_plan", tenant.organization)}
            >
              <RefreshCw />
              Change plan
            </Button>
            <div className="space-y-1.5 pt-2">
              <Label htmlFor="quota">Temporary quota boost</Label>
              <Input
                id="quota"
                value={quotaBoost}
                onChange={(e) => setQuotaBoost(e.target.value)}
                placeholder="+50000 messages"
                className="font-mono"
              />
              <Button
                type="button"
                size="sm"
                className="w-full"
                onClick={() => previewAdminAction("quota.override", `${tenant.organization} · ${quotaBoost || "unset"}`)}
              >
                Override quota
              </Button>
            </div>
          </div>
        </SectionPanel>

        <SectionPanel title="Feature & rate controls" description="Per-tenant capability switches">
          <div className="space-y-4">
            {(
              [
                ["apiAccess", "API access"],
                ["webhooks", "Webhooks"],
                ["campaigns", "Campaigns"],
                ["customDomains", "Custom domains"],
              ] as const
            ).map(([key, label]) => (
              <div key={key} className="flex items-center justify-between gap-3">
                <Label htmlFor={key}>{label}</Label>
                <Switch
                  id={key}
                  checked={features[key]}
                  onCheckedChange={(checked) => {
                    setFeatures((prev) => ({ ...prev, [key]: checked }));
                    previewAdminAction(`feature.${key}`, `${tenant.organization} · ${checked ? "on" : "off"}`);
                  }}
                />
              </div>
            ))}
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("ratelimit.set", tenant.organization)}
            >
              <PauseCircle />
              Adjust rate limits
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("domains.reset_verification", tenant.organization)}
            >
              <Lock />
              Reset domain verification
            </Button>
          </div>
        </SectionPanel>

        <SectionPanel title="Compliance & notes" description="Internal operator workspace">
          <div className="space-y-3">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("compliance.flag_review", tenant.organization)}
            >
              <ShieldAlert />
              Flag for review
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("compliance.schedule_deletion", tenant.organization)}
            >
              <Ban />
              Schedule deletion
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => previewAdminAction("tenant.export", tenant.organization)}
            >
              <PlayCircle />
              Export tenant data
            </Button>
            <div className="space-y-1.5 pt-1">
              <Label htmlFor="note">Internal note</Label>
              <Textarea id="note" value={note} onChange={(e) => setNote(e.target.value)} rows={4} />
              <Button
                type="button"
                size="sm"
                className="w-full"
                onClick={() => previewAdminAction("tenant.note_save", tenant.organization)}
              >
                <NotebookPen />
                Save note
              </Button>
            </div>
          </div>
        </SectionPanel>
      </div>

      <SectionPanel title="Send message to tenant" description="Billing, system, or compliance notification">
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="msg-subject">Subject</Label>
            <Input id="msg-subject" value={messageSubject} onChange={(e) => setMessageSubject(e.target.value)} />
          </div>
          <div className="flex items-end">
            <Button asChild variant="ghost" size="sm">
              <Link href="/admin/messages">Open message center</Link>
            </Button>
          </div>
        </div>
        <div className="mt-3 space-y-1.5">
          <Label htmlFor="msg-body">Body</Label>
          <Textarea id="msg-body" value={messageBody} onChange={(e) => setMessageBody(e.target.value)} rows={4} />
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button
            type="button"
            onClick={() =>
              previewAdminAction("message.send", `${tenant.organization} · ${messageSubject || "untitled"}`)
            }
            disabled={!messageSubject.trim() || !messageBody.trim()}
          >
            <Mail />
            Send to owner
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => previewAdminAction("message.send_all_users", tenant.organization)}
            disabled={!messageSubject.trim() || !messageBody.trim()}
          >
            Send to all users
          </Button>
        </div>
      </SectionPanel>

      <Alert variant="warning">
        <AlertTitle>Operator caution</AlertTitle>
        <AlertDescription>
          Impersonation, suspension, key revocation, and deletion must remain audited server-side. This screen is the control
          surface — not the source of truth.
        </AlertDescription>
      </Alert>

      <Dialog open={!!confirm} onOpenChange={(open) => !open && setConfirm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{confirm?.title}</DialogTitle>
            <DialogDescription>{confirm?.description}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setConfirm(null)}>
              Cancel
            </Button>
            <Button
              type="button"
              variant={confirm?.tone === "danger" ? "destructive" : "primary"}
              onClick={runConfirmed}
            >
              Confirm
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
