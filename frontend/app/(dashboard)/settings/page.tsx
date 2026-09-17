"use client";

import { ThemeToggle } from "@/components/layout/theme-toggle";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { useStoredUser } from "@/hooks/use-stored-user";
import { initials } from "@/lib/format";

export default function SettingsPage() {
  const user = useStoredUser();

  return (
    <div className="flex min-h-full flex-col gap-6">
      <PageHeader
        eyebrow="Account"
        title="Settings"
        description="Workspace appearance and account details from the signed-in session."
      />

      <SectionPanel elevated>
        <div className="flex items-center gap-4">
          <Avatar className="size-14">
            <AvatarFallback className="text-base">{initials(user?.organization || user?.email || "T")}</AvatarFallback>
          </Avatar>
          <div className="min-w-0">
            <p className="font-display text-2xl tracking-tight">{user?.organization ?? "Workspace"}</p>
            <p className="text-sm text-muted-foreground">{user?.email}</p>
          </div>
        </div>
      </SectionPanel>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionPanel title="Appearance" description="Paper in light. Charcoal in dark. The rail stays ink.">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium">Theme</p>
              <p className="text-sm text-muted-foreground">Toggle between light and dark.</p>
            </div>
            <ThemeToggle />
          </div>
        </SectionPanel>

        <SectionPanel title="Notifications" description="Preferences persist through a settings API later">
          <div className="flex items-center justify-between gap-4">
            <Label htmlFor="delivery-alerts">Delivery alerts</Label>
            <Switch id="delivery-alerts" disabled />
          </div>
        </SectionPanel>
      </div>

      <SectionPanel title="Account" description="Profile updates will use /api/v1/auth/me" className="flex-1">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <p className="tech-label">Organization</p>
            <p className="mt-1 text-sm">{user?.organization ?? "—"}</p>
          </div>
          <div>
            <p className="tech-label">Role</p>
            <p className="mt-1 text-sm">{user?.role ?? "—"}</p>
          </div>
          <div>
            <p className="tech-label">Email</p>
            <p className="mt-1 font-mono text-sm">{user?.email ?? "—"}</p>
          </div>
          <div>
            <p className="tech-label">Tenant ID</p>
            <p className="mt-1 font-mono text-xs">{user?.tenantId ?? "—"}</p>
          </div>
        </div>
        <Separator className="my-4" />
        <p className="text-[11px] text-muted-foreground">Session values only. No fabricated account metadata.</p>
      </SectionPanel>
    </div>
  );
}
