"use client";

import { FormEvent, useState } from "react";
import { toast } from "sonner";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiClientError } from "@/lib/api";
import { previewAdminAction } from "@/lib/admin/actions";
import { registerAccount } from "@/services/auth";

export default function AdminCreateAccountPage() {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ organization: string; email: string; tenantId: string } | null>(null);
  const [plan, setPlan] = useState("starter");

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setCreated(null);
    try {
      const organization = String(form.get("organization") ?? "").trim();
      const email = String(form.get("email") ?? "").trim();
      const password = String(form.get("password") ?? "");
      const session = await registerAccount({ organization, email, password });
      // Do not adopt the tenant session — admin stays in the admin console.
      setCreated({
        organization: session.user.organization,
        email: session.user.email,
        tenantId: session.user.tenantId,
      });
      previewAdminAction("tenant.provision", `${organization} · plan ${plan}`);
      toast.success("Tenant account created");
      event.currentTarget.reset();
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to create account.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Platform admin"
        title="Create account"
        description="Only platform admins can provision tenant workspaces. Public registration is disabled."
      />
      <AdminPreviewBanner />

      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <SectionPanel title="Provision tenant" description="Calls POST /api/v1/auth/register under admin authority">
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="organization">Organization</Label>
              <Input id="organization" name="organization" required autoComplete="organization" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">Owner email</Label>
              <Input id="email" name="email" type="email" required autoComplete="off" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Temporary password</Label>
              <Input id="password" name="password" type="password" required minLength={8} autoComplete="new-password" />
              <p className="text-[11px] text-muted-foreground">Share securely with the owner. They sign in at /login.</p>
            </div>
            <div className="space-y-1.5">
              <Label>Initial plan</Label>
              <Select value={plan} onValueChange={setPlan}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="starter">Starter</SelectItem>
                  <SelectItem value="growth">Growth</SelectItem>
                  <SelectItem value="scale">Scale</SelectItem>
                </SelectContent>
              </Select>
              <p className="text-[11px] text-muted-foreground">Plan assignment will bind to entitlements API later.</p>
            </div>
            <Button type="submit" loading={submitting}>
              Create tenant account
            </Button>
          </form>
          {error ? (
            <Alert variant="error" className="mt-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          {created ? (
            <Alert variant="success" className="mt-4">
              <AlertTitle>Account provisioned</AlertTitle>
              <AlertDescription>
                <p className="mt-1">{created.organization}</p>
                <p className="mt-1 font-mono text-xs">{created.email}</p>
                <p className="mt-1 font-mono text-xs">tenant {created.tenantId}</p>
              </AlertDescription>
            </Alert>
          ) : null}
        </SectionPanel>

        <SectionPanel title="Provisioning policy">
          <ul className="space-y-3 text-sm text-muted-foreground">
            <li>Public /register is closed. Owners cannot self-serve signup.</li>
            <li>Admin creates the org + owner; credentials are delivered out of band.</li>
            <li>Use Messages to send welcome / billing onboarding notices.</li>
            <li>Suspend or permanently delete from the tenant control screen.</li>
          </ul>
        </SectionPanel>
      </div>
    </div>
  );
}
