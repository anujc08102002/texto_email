"use client";

import { useState } from "react";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { previewAdminAction } from "@/lib/admin/actions";
import { ADMIN_PLATFORM_FLAGS } from "@/lib/presentation/admin";

export function AdminPlatformScreen() {
  const [flags, setFlags] = useState(ADMIN_PLATFORM_FLAGS);

  function toggle<K extends keyof typeof flags>(key: K, label: string) {
    setFlags((prev) => {
      const next = { ...prev, [key]: !prev[key] };
      previewAdminAction(`platform.flag.${key}`, `${label} · ${next[key] ? "enabled" : "disabled"}`);
      return next;
    });
  }

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Platform admin"
        title="Platform controls"
        description="Global feature flags, maintenance, and fleet-wide safeguards."
      />
      <AdminPreviewBanner />

      <SectionPanel title="Feature flags">
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <Label>Maintenance mode</Label>
              <p className="text-xs text-muted-foreground">Serve status page; block mutations</p>
            </div>
            <Switch checked={flags.maintenanceMode} onCheckedChange={() => toggle("maintenanceMode", "Maintenance mode")} />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <Label>New signups</Label>
              <p className="text-xs text-muted-foreground">Allow public registration</p>
            </div>
            <Switch checked={flags.newSignups} onCheckedChange={() => toggle("newSignups", "New signups")} />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <Label>Global send throttle</Label>
              <p className="text-xs text-muted-foreground">Emergency rate limit across all tenants</p>
            </div>
            <Switch
              checked={flags.globalSendThrottle}
              onCheckedChange={() => toggle("globalSendThrottle", "Global send throttle")}
            />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <Label>Require DNS verified</Label>
              <p className="text-xs text-muted-foreground">Block unverified domains from sending</p>
            </div>
            <Switch
              checked={flags.requireDnsVerified}
              onCheckedChange={() => toggle("requireDnsVerified", "Require DNS verified")}
            />
          </div>
        </div>
      </SectionPanel>

      <SectionPanel title="Emergency actions">
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => previewAdminAction("platform.drain_queues")}>
            Drain retry queues
          </Button>
          <Button variant="secondary" onClick={() => previewAdminAction("platform.flush_webhook_retries")}>
            Flush webhook retries
          </Button>
          <Button variant="destructive" onClick={() => previewAdminAction("platform.halt_all_sending")}>
            Halt all sending
          </Button>
        </div>
      </SectionPanel>
    </div>
  );
}
