"use client";

import { FormEvent, useState } from "react";
import { toast } from "sonner";
import Link from "next/link";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiClientError } from "@/lib/api";
import { useAdminResource } from "@/hooks/use-admin-resource";
import type { Plan } from "@/types/api";
import { adminGet, provisionTenant } from "@/services/admin-ops";

export default function AdminCreateAccountPage() {
  const plans = useAdminResource(() => adminGet<Plan[]>("/api/v1/plans"), []);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ organization: string; email: string; tenantId: string } | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setSubmitting(true);
    setError(null);
    setCreated(null);
    try {
      const organization = String(form.get("organization") ?? "").trim();
      const email = String(form.get("email") ?? "").trim();
      const password = String(form.get("password") ?? "");
      const account = await provisionTenant({ organization, email, password, planCode: String(form.get("planCode") ?? "FREE") });
      // Do not adopt the tenant session — admin stays in the admin console.
      setCreated({
        organization: account.organization,
        email: account.email,
        tenantId: account.tenantId,
      });
      toast.success("Tenant account created");
      formElement.reset();
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

      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <SectionPanel title="Provision tenant" description="Create an organization and its owner with the selected subscription entitlements.">
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="organization">Organization</Label>
              <Input id="organization" name="organization" required maxLength={255} autoComplete="organization" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">Owner email</Label>
              <Input id="email" name="email" type="email" required maxLength={320} autoComplete="off" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Temporary password</Label>
              <Input id="password" name="password" type="password" required minLength={8} maxLength={128} autoComplete="new-password" />
              <p className="text-[11px] text-muted-foreground">Share securely with the owner. They sign in at /login.</p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="planCode">Initial plan</Label>
              <select id="planCode" name="planCode" defaultValue="FREE" className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm">
                {(plans.data ?? [{ code: "FREE", name: "Free" }]).map((plan) => <option key={plan.code} value={plan.code}>{plan.name}</option>)}
              </select>
              <p className="text-xs text-muted-foreground">Admin grant only. This enables the plan features without charging the customer. Choose Starter or above to use webhooks.</p>
              {plans.error ? <p className="text-xs text-destructive">{plans.error} Only Free can be selected until the catalog loads.</p> : null}
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
                <Link className="mt-3 inline-block underline" href={`/admin/tenants/${created.tenantId}`}>Open tenant details</Link>
              </AlertDescription>
            </Alert>
          ) : null}
        </SectionPanel>

        <SectionPanel title="Provisioning policy">
          <ul className="space-y-3 text-sm text-muted-foreground">
            <li>Public /register is closed. Owners cannot self-serve signup.</li>
            <li>Admin creates the org + owner; credentials are delivered out of band.</li>
            <li>The owner signs in, adds their sending domain, publishes the DNS records and verifies the domain.</li>
            <li>Manage access from tenant details. The initial plan is an admin grant. Recurring payments require a separately configured billing provider.</li>
          </ul>
        </SectionPanel>
      </div>
    </div>
  );
}
