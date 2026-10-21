"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SectionPanel } from "@/components/ops/section-panel";
import { ApiClientError } from "@/lib/api";
import { adminGet } from "@/services/admin-ops";

type Readiness = {
  status: string; region: string; configurationSetName: string | null;
  checks: Record<string, string>; max24HourSend: number | null; sentLast24Hours: number | null;
  maxSendRate: number | null; checkedAt: string; note: string;
};

export function SesReadinessPanel() {
  const [identity, setIdentity] = useState("");
  const [report, setReport] = useState<Readiness | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function inspect() {
    setBusy(true); setError(null);
    try { setReport(await adminGet<Readiness>(`/api/v1/admin/delivery/ses/readiness?identity=${encodeURIComponent(identity.trim())}`)); }
    catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "Unable to check SES readiness."); }
    finally { setBusy(false); }
  }

  return <SectionPanel title="Amazon SES readiness" description="Check the existing SES account, sending identity, quotas, and feedback destinations. This check does not send email.">
    <form className="flex flex-wrap items-end gap-3" onSubmit={(event) => { event.preventDefault(); void inspect(); }}>
      <div className="min-w-56 flex-1 space-y-1.5"><Label htmlFor="ses-identity">Sending domain or verified email</Label><Input id="ses-identity" value={identity} onChange={(event) => setIdentity(event.target.value)} placeholder="example.com" maxLength={320} required /></div>
      <Button type="submit" loading={busy} disabled={!identity.trim()}>Check SES</Button>
    </form>
    {error ? <p role="alert" className="mt-3 text-sm text-destructive">{error}</p> : null}
    {report ? <div className="mt-4 space-y-4">
      <p className="text-sm font-medium">{report.status.replaceAll("_", " ")} · {report.region} · {report.configurationSetName || "No configuration set"}</p>
      <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{Object.entries(report.checks).map(([key, value]) => <div className="rounded-lg border p-3" key={key}><dt className="text-xs text-muted-foreground">{key.replace(/([A-Z])/g, " $1")}</dt><dd className="mt-1 text-sm font-medium">{value.replaceAll("_", " ")}</dd></div>)}</dl>
      {report.max24HourSend !== null ? <p className="text-sm">SES quota: {report.sentLast24Hours ?? 0} / {report.max24HourSend} messages over 24 hours · {report.maxSendRate ?? "—"} / second</p> : null}
      <p className="text-xs text-muted-foreground">{report.note}</p>
    </div> : null}
  </SectionPanel>;
}
