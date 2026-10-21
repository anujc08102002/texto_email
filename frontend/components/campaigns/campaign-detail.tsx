"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { CampaignComposer } from "@/components/campaigns/campaign-composer";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, ChevronLeft, ChevronRight } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { SectionPanel } from "@/components/ops/section-panel";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import {
  getCampaign,
  cloneCampaign,
  exportCampaignReport,
  getCampaignReport,
  listCampaignRecipients,
  resumeCampaign,
  stopCampaign,
  type Campaign,
  type CampaignRecipientPage,
  type CampaignReport,
} from "@/services/campaigns";

function statusText(status: string) {
  return status.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function CampaignDetail({ campaignId }: { campaignId: string }) {
  const router = useRouter();
  const [campaign, setCampaign] = useState<Campaign | null>(null);
  const [report, setReport] = useState<CampaignReport | null>(null);
  const [recipientPage, setRecipientPage] = useState<CampaignRecipientPage | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [recipientsLoading, setRecipientsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  const loadCampaign = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [current, metrics] = await Promise.all([getCampaign(campaignId), getCampaignReport(campaignId)]);
      setCampaign(current);
      setReport(metrics);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load campaign.");
    } finally {
      setLoading(false);
    }
  }, [campaignId]);

  const loadRecipients = useCallback(async () => {
    setRecipientsLoading(true);
    try {
      setRecipientPage(await listCampaignRecipients(campaignId, page, 20));
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load campaign recipients.");
    } finally {
      setRecipientsLoading(false);
    }
  }, [campaignId, page]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadCampaign(), 0);
    return () => window.clearTimeout(timer);
  }, [loadCampaign]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadRecipients(), 0);
    return () => window.clearTimeout(timer);
  }, [loadRecipients]);

  const campaignStatus = campaign?.status;
  useEffect(() => {
    if (!campaignStatus || !["QUEUED", "PROCESSING", "SCHEDULED"].includes(campaignStatus)) return;
    let active = true;
    const timer = window.setInterval(() => {
      void Promise.all([getCampaign(campaignId), getCampaignReport(campaignId), listCampaignRecipients(campaignId, page, 20)])
        .then(([current, metrics, recipients]) => { if (active) { setCampaign(current); setReport(metrics); setRecipientPage(recipients); } })
        .catch(() => { /* Keep the last result; manual refresh remains available. */ });
    }, 5000);
    return () => { active = false; window.clearInterval(timer); };
  }, [campaignId, campaignStatus, page]);

  async function control(action: () => Promise<Campaign>, message: string) {
    setActionBusy(true);
    try {
      setCampaign(await action());
      toast.success(message);
      await loadCampaign();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Campaign action failed.");
    } finally {
      setActionBusy(false);
    }
  }

  async function downloadReport() {
    setActionBusy(true);
    try {
      const blob = await exportCampaignReport(campaignId);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a"); link.href = url; link.download = `campaign-${campaignId}.csv`; link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to export report."); }
    finally { setActionBusy(false); }
  }

  async function clone() {
    setActionBusy(true);
    try { const draft = await cloneCampaign(campaignId); toast.success("Copied to a new draft. Choose its audience before sending."); router.push(`/campaigns/${draft.id}`); }
    catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to clone campaign."); }
    finally { setActionBusy(false); }
  }

  if (loading && !campaign) return <LoadingState label="Loading campaign" />;
  if (error && !campaign) return <ErrorState description={error} onRetry={() => void loadCampaign()} />;
  if (!campaign) return null;

  if (campaign && ["DRAFT", "SCHEDULED"].includes(campaign.status)) {
    return <div className="space-y-6"><PageHeader title={campaign.name} actions={<Button variant="outline" asChild><Link href="/campaigns">Back to campaigns</Link></Button>} /><CampaignComposer initialCampaignId={campaignId} onCampaignChange={setCampaign} /></div>;
  }

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Campaign"
        title={campaign.name}
        description={`Status: ${statusText(campaign.status)}`}
        actions={<><Button variant="outline" loading={actionBusy} onClick={() => void downloadReport()}>Export CSV</Button><Button variant="outline" loading={actionBusy} onClick={() => void clone()}>Clone campaign</Button><Button variant="outline" asChild><Link href="/campaigns"><ArrowLeft className="size-4" />Campaigns</Link></Button></>}
      />

      {error ? <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{error}</div> : null}

      <SectionPanel title="Campaign information">
        <div className="grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <p><span className="text-muted-foreground">Sender</span><br />{campaign.sender || "-"}</p>
          <p><span className="text-muted-foreground">Reply-to</span><br />{campaign.replyTo || "-"}</p>
          <p><span className="text-muted-foreground">Subject</span><br />{campaign.subject || (campaign.templateId ? "Template content" : "-")}</p>
          <p><span className="text-muted-foreground">Updated</span><br />{formatDateTime(campaign.updatedAt)}</p>
          <p><span className="text-muted-foreground">Schedule</span><br />{campaign.scheduledAt ? `${formatDateTime(campaign.scheduledAt)} (${campaign.timezone || "UTC"})` : "Not scheduled"}</p>
          <p><span className="text-muted-foreground">Recipients</span><br />{report?.total ?? "-"}</p>
          <p><span className="text-muted-foreground">Template</span><br />{campaign.templateId || "Inline content"}</p>
        </div>
      </SectionPanel>

      <SectionPanel title="Delivery summary">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">
          {[
            ["Pending", report?.pending],
            ["Queued", report?.queued],
            ["Accepted by MTA", report?.accepted],
            ["Delivered", report?.delivered],
            ["Bounced", report?.bounced],
            ["Complained", report?.complained],
            ["Failed", report?.failed],
            ["Suppressed", report?.suppressed],
          ].map(([label, value]) => (
            <div key={label} className="rounded-md border bg-card px-3 py-2">
              <p className="text-xs text-muted-foreground">{label}</p>
              <p className="text-lg font-semibold">{value ?? "-"}</p>
            </div>
          ))}
        </div>
        <p className="mt-3 text-xs text-muted-foreground">Accepted by the mail transport is not proof of recipient delivery.</p>
      </SectionPanel>

      <SectionPanel title="Recipients" description="Recipient status is linked to the existing email message, bounce, and complaint records.">
        {recipientsLoading ? <LoadingState label="Loading recipients" /> : null}
        {!recipientsLoading && (recipientPage?.items.length ?? 0) === 0 ? (
          <EmptyState title="No recipients" description="No campaign recipients have been added." />
        ) : null}
        {!recipientsLoading && recipientPage && recipientPage.items.length > 0 ? (
          <>
            <div className="overflow-x-auto rounded-lg border">
              <Table>
                <TableHeader><TableRow><TableHead>Email</TableHead><TableHead>Status</TableHead><TableHead>Added</TableHead><TableHead>Error</TableHead></TableRow></TableHeader>
                <TableBody>
                  {recipientPage.items.map((recipient) => (
                    <TableRow key={recipient.id}>
                      <TableCell className="font-mono text-xs">{recipient.email}</TableCell>
                      <TableCell>{statusText(recipient.status)}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">{formatDateTime(recipient.createdAt)}</TableCell>
                      <TableCell className="max-w-xs truncate text-destructive">{recipient.errorMessage || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <div className="mt-3 flex items-center justify-end gap-3 text-sm text-muted-foreground">
              <span>Page {page + 1} of {Math.max(1, recipientPage.totalPages)}</span>
              <Button size="icon" variant="outline" aria-label="Previous recipients" disabled={page <= 0 || recipientsLoading} onClick={() => setPage((current) => Math.max(0, current - 1))}><ChevronLeft className="size-4" /></Button>
              <Button size="icon" variant="outline" aria-label="Next recipients" disabled={page + 1 >= recipientPage.totalPages || recipientsLoading} onClick={() => setPage((current) => current + 1)}><ChevronRight className="size-4" /></Button>
            </div>
          </>
        ) : null}
      </SectionPanel>

      {campaign.status === "SCHEDULED" || campaign.status === "QUEUED" || campaign.status === "PROCESSING" ? (
        <div className="flex justify-end"><Button variant="destructive" loading={actionBusy} onClick={() => void control(() => stopCampaign(campaign.id), "Campaign stopped")}>Stop campaign</Button></div>
      ) : null}
      {campaign.status === "STOPPED" ? (
        <div className="flex justify-end"><Button variant="outline" loading={actionBusy} onClick={() => void control(() => resumeCampaign(campaign.id), "Campaign resumed")}>Resume campaign</Button></div>
      ) : null}
    </div>
  );
}