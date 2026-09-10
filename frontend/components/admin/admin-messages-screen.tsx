"use client";

import { useState } from "react";
import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
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
import { Textarea } from "@/components/ui/textarea";
import { previewAdminAction } from "@/lib/admin/actions";
import { ADMIN_MESSAGE_TEMPLATES, ADMIN_TENANTS } from "@/lib/presentation/admin";

export function AdminMessagesScreen() {
  const [tenantId, setTenantId] = useState(ADMIN_TENANTS[0]?.id ?? "");
  const [templateId, setTemplateId] = useState(ADMIN_MESSAGE_TEMPLATES[0]?.id ?? "");
  const template = ADMIN_MESSAGE_TEMPLATES.find((t) => t.id === templateId);
  const tenant = ADMIN_TENANTS.find((t) => t.id === tenantId);
  const [subject, setSubject] = useState(template?.subject.replace("{{organization}}", tenant?.organization ?? "") ?? "");
  const [body, setBody] = useState(template?.body ?? "");

  function applyTemplate(id: string) {
    setTemplateId(id);
    const next = ADMIN_MESSAGE_TEMPLATES.find((t) => t.id === id);
    const org = ADMIN_TENANTS.find((t) => t.id === tenantId)?.organization ?? "{{organization}}";
    if (next) {
      setSubject(next.subject.replaceAll("{{organization}}", org));
      setBody(next.body);
    }
  }

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Platform admin"
        title="Tenant messages"
        description="Send billing, system, and compliance notices to workspace owners or all users."
      />
      <AdminPreviewBanner />

      <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
        <SectionPanel title="Templates" description="Reusable operator notices">
          <div className="space-y-2">
            {ADMIN_MESSAGE_TEMPLATES.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => applyTemplate(item.id)}
                className={`w-full rounded-xl border px-3 py-3 text-left transition-colors ${
                  templateId === item.id ? "border-primary/40 bg-primary/5" : "border-border/70 hover:bg-muted/50"
                }`}
              >
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium">{item.name}</p>
                  <Badge variant="outline">{item.channel}</Badge>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">{item.subject}</p>
              </button>
            ))}
          </div>
        </SectionPanel>

        <SectionPanel title="Compose" description="Preview delivery until admin messaging API exists">
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label>Tenant</Label>
              <Select
                value={tenantId}
                onValueChange={(value) => {
                  setTenantId(value);
                  const org = ADMIN_TENANTS.find((t) => t.id === value)?.organization ?? "";
                  setSubject((prev) => prev.replace(/for .+$/, `for ${org}`).replace(/— .+$/, `— ${org}`));
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select tenant" />
                </SelectTrigger>
                <SelectContent>
                  {ADMIN_TENANTS.map((item) => (
                    <SelectItem key={item.id} value={item.id}>
                      {item.organization}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="subject">Subject</Label>
              <Input id="subject" value={subject} onChange={(e) => setSubject(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="body">Body</Label>
              <Textarea id="body" value={body} onChange={(e) => setBody(e.target.value)} rows={8} />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                disabled={!subject.trim() || !body.trim()}
                onClick={() => previewAdminAction("message.send_owner", tenant?.organization ?? tenantId)}
              >
                Send to owner
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={!subject.trim() || !body.trim()}
                onClick={() => previewAdminAction("message.send_all_users", tenant?.organization ?? tenantId)}
              >
                Send to all users
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => previewAdminAction("message.broadcast_status", "all past_due")}
              >
                Broadcast to past due
              </Button>
            </div>
          </div>
        </SectionPanel>
      </div>
    </div>
  );
}
