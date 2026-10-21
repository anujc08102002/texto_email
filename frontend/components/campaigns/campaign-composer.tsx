"use client";

import { HtmlPreview } from "@/components/templates/html-preview";

import { AttachmentPicker } from "@/components/attachments/attachment-picker";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CalendarClock, Check, ChevronLeft, ChevronRight, Eye, Send, Users } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingState } from "@/components/ui/loading-state";
import { SectionPanel } from "@/components/ops/section-panel";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import { listSegments, previewSavedSegment, type Segment } from "@/services/segments";
import { listContacts, type Contact, type ContactPage } from "@/services/contacts";
import { getTemplateVersion, listTemplates } from "@/services/templates";
import type { Template, TemplateVersion } from "@/types/api";
import {
  addCampaignRecipients,
  listCampaignRecipients,
  removeCampaignRecipient,
  type CampaignRecipientPage,
  addCampaignAudience,
  previewCampaign,
  testCampaign,
  type CampaignPreview,
  getCampaign,
  getCampaignContent,
  updateCampaignDraft,
  getCampaignReport,
  resumeCampaign,
  saveCampaignDraft,
  scheduleCampaign,
  sendCampaignNow,
  stopCampaign,
  rescheduleCampaign,
  type Campaign,
  type CampaignDraftInput,
  type CampaignReport,
} from "@/services/campaigns";

const emptyDraft: CampaignDraftInput = {
  name: "",
  sender: "",
  replyTo: "",
  subject: "",
  preheader: "",
  htmlBody: "",
  textBody: "",
};

function toLocalInputValue(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function statusLabel(status: string) {
  return status.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function CampaignComposer({ initialCampaignId, onCampaignChange }: { initialCampaignId?: string; onCampaignChange?: (campaign: Campaign) => void }) {
  const router = useRouter();
  const [draft, setDraft] = useState<CampaignDraftInput>(emptyDraft);
  const [campaign, setCampaign] = useState<Campaign | null>(null);
  const [initialLoading, setInitialLoading] = useState(Boolean(initialCampaignId));
  const [editing, setEditing] = useState(false);
  const [segments, setSegments] = useState<Segment[]>([]);
  const [audience, setAudience] = useState("");
  const [audienceCount, setAudienceCount] = useState<number | null>(null);
  const [audienceBusy, setAudienceBusy] = useState(false);
  const [audienceConfirmOpen, setAudienceConfirmOpen] = useState(false);
  const [attachmentBusy, setAttachmentBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [contactsPage, setContactsPage] = useState<ContactPage | null>(null);
  const [contactsLoading, setContactsLoading] = useState(false);
  const [contactError, setContactError] = useState<string | null>(null);
  const [contactSearch, setContactSearch] = useState("");
  const [contactPageNumber, setContactPageNumber] = useState(0);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [addedAudience, setAddedAudience] = useState<CampaignRecipientPage | null>(null);
  const [addedPage, setAddedPage] = useState(0);
  const [addedError, setAddedError] = useState<string | null>(null);
  const [recipientBusy, setRecipientBusy] = useState(false);
  const [addingRecipients, setAddingRecipients] = useState(false);
  const [report, setReport] = useState<CampaignReport | null>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [templateVersion, setTemplateVersion] = useState<TemplateVersion | null>(null);
  const [templateErrorState, setTemplateErrorState] = useState<{ id: string; message: string } | null>(null);
  const [scheduleLocal, setScheduleLocal] = useState(() => toLocalInputValue(new Date(Date.now() + 3_600_000)));
  const [confirmSendOpen, setConfirmSendOpen] = useState(false);
  const [preview, setPreview] = useState<CampaignPreview | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [testTo, setTestTo] = useState("");
  const [testBusy, setTestBusy] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);

  const refreshReport = useCallback(async (id: string) => {
    try {
      setReport(await getCampaignReport(id));
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load campaign recipients.");
    }
  }, []);

  const loadContacts = useCallback(async () => {
    setContactsLoading(true);
    setContactError(null);
    try {
      setContactsPage(await listContacts(contactPageNumber, 20, contactSearch));
    } catch (cause) {
      setContactError(cause instanceof ApiClientError ? cause.message : "Unable to load contacts.");
    } finally {
      setContactsLoading(false);
    }
  }, [contactPageNumber, contactSearch]);

  useEffect(() => {
    let active = true;
    void listTemplates()
      .then((result) => {
        if (active) {
          setTemplates(result.filter((template) => template.status !== "ARCHIVED"));
          setTemplateErrorState(null);
        }
      })
      .catch((cause) => {
        if (active) {
          setTemplateErrorState({
            id: "list",
            message: cause instanceof ApiClientError ? cause.message : "Unable to load templates.",
          });
        }
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const selected = templates.find((template) => template.id === draft.templateId);
    if (!selected?.currentVersion) return;
    let active = true;
    void getTemplateVersion(selected.id, selected.currentVersion)
      .then((version) => { if (active) setTemplateVersion(version); })
      .catch((cause) => { if (active) setTemplateErrorState({ id: selected.id, message: cause instanceof ApiClientError ? cause.message : "Unable to load the active template version." }); });
    return () => { active = false; };
  }, [draft.templateId, templates]);

  useEffect(() => {
    if (!campaign || campaign.status !== "DRAFT") return;
    const timer = window.setTimeout(() => void loadContacts(), 250);
    return () => window.clearTimeout(timer);
  }, [campaign, loadContacts]);

  useEffect(() => {
    if (!initialCampaignId) return;
    let active = true;
    void Promise.all([getCampaign(initialCampaignId), getCampaignContent(initialCampaignId), getCampaignReport(initialCampaignId)])
      .then(([saved, content, metrics]) => {
        if (!active) return;
        setCampaign(saved);
        setDraft(Object.fromEntries(Object.entries(content).map(([key, value]) => [key, value ?? ""])) as CampaignDraftInput);
        setReport(metrics);
        if (saved.scheduledAt) setScheduleLocal(toLocalInputValue(new Date(saved.scheduledAt)));
      })
      .catch((cause) => { if (active) setError(cause instanceof ApiClientError ? cause.message : "Unable to load draft."); })
      .finally(() => { if (active) setInitialLoading(false); });
    return () => { active = false; };
  }, [initialCampaignId]);

  useEffect(() => {
    let active = true;
    void listSegments().then((items) => { if (active) setSegments(items); })
      .catch(() => { /* Contacts remain available; audience evaluation reports errors. */ });
    return () => { active = false; };
  }, [campaign?.id]);

  async function reviewAudience() {
    if (!audience) return;
    setAudienceBusy(true);
    try {
      const count = audience === "__all"
        ? (await listContacts(0, 1, "")).totalElements
        : (await previewSavedSegment(audience, 0, 1)).totalElements;
      setAudienceCount(count);
      setAudienceConfirmOpen(true);
    } catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to evaluate audience."); }
    finally { setAudienceBusy(false); }
  }

  async function addAudience() {
    if (!campaign || !audience) return;
    setAudienceBusy(true);
    try {
      const result = await addCampaignAudience(campaign.id, audience === "__all" ? { allSubscribed: true } : { segmentId: audience });
      setAudienceConfirmOpen(false);
      await refreshReport(campaign.id);
      toast.success(`${result.added} recipients added`);
    } catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to add audience."); }
    finally { setAudienceBusy(false); }
  }

  useEffect(() => {
    if (!campaign?.id) return;
    let active = true;
    void listCampaignRecipients(campaign.id, addedPage, 20)
      .then((result) => { if (active) { setAddedAudience(result); setAddedError(null); } })
      .catch((cause) => { if (active) setAddedError(cause instanceof ApiClientError ? cause.message : "Unable to load added recipients."); });
    return () => { active = false; };
  }, [campaign?.id, addedPage, report?.total]);

  async function removeRecipient(recipientId: string) {
    if (!campaign) return;
    setRecipientBusy(true);
    try {
      await removeCampaignRecipient(campaign.id, recipientId);
      if (addedAudience?.items.length === 1 && addedPage > 0) setAddedPage((page) => page - 1);
      await refreshReport(campaign.id);
      toast.success("Recipient removed");
    } catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to remove recipient."); }
    finally { setRecipientBusy(false); }
  }

  async function createDraft() {
    setSaving(true);
    setError(null);
    try {
      const input: CampaignDraftInput = {
        ...draft,
        sender: draft.sender?.trim() || undefined,
        replyTo: draft.replyTo?.trim() || undefined,
        subject: draft.subject?.trim() || undefined,
        preheader: draft.preheader?.trim() || undefined,
        htmlBody: draft.htmlBody?.trim() || undefined,
        textBody: draft.textBody?.trim() || undefined,
        templateId: draft.templateId || undefined,
      };
      const saved = campaign ? await updateCampaignDraft(campaign.id, input) : await saveCampaignDraft(input);
      setCampaign(saved);
      setEditing(false);
      toast.success("Campaign draft saved");
      await refreshReport(saved.id);
      if (!campaign) router.replace(`/campaigns/${saved.id}`);
    } catch (cause) {
      const message = cause instanceof ApiClientError ? cause.message : "Unable to save campaign draft.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  function toggleContact(contactId: string) {
    setSelectedIds((current) => current.includes(contactId)
      ? current.filter((id) => id !== contactId)
      : [...current, contactId]);
  }

  function togglePage(contacts: Contact[]) {
    const pageIds = contacts
      .filter((contact) => contact.status === "ACTIVE" && contact.subscriptionStatus === "SUBSCRIBED")
      .map((contact) => contact.id);
    const allSelected = pageIds.length > 0 && pageIds.every((id) => selectedIds.includes(id));
    setSelectedIds((current) => allSelected
      ? current.filter((id) => !pageIds.includes(id))
      : [...new Set([...current, ...pageIds])]);
  }

  async function addSelectedRecipients() {
    if (!campaign || selectedIds.length === 0) return;
    setAddingRecipients(true);
    try {
      const added = await addCampaignRecipients(campaign.id, selectedIds);
      setSelectedIds([]);
      await refreshReport(campaign.id);
      toast.success(`${added.length} recipient${added.length === 1 ? "" : "s"} added`);
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to add recipients.");
    } finally {
      setAddingRecipients(false);
    }
  }

  async function runAction(action: () => Promise<Campaign>, successMessage: string) {
    if (!campaign) return;
    setActionBusy(true);
    setError(null);
    try {
      const updated = await action();
      setCampaign(updated);
      onCampaignChange?.(updated);
      toast.success(successMessage);
      await refreshReport(updated.id);
    } catch (cause) {
      const message = cause instanceof ApiClientError ? cause.message : "Campaign action failed.";
      setError(message);
      toast.error(message);
    } finally {
      setActionBusy(false);
    }
  }

  async function showPreview() {
    if (!campaign) return;
    setPreviewBusy(true);
    try { setPreview(await previewCampaign(campaign.id)); setPreviewOpen(true); }
    catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to render campaign."); }
    finally { setPreviewBusy(false); }
  }

  async function sendTest() {
    if (!campaign || !testTo.trim()) return;
    setTestBusy(true);
    try { const message = await testCampaign(campaign.id, testTo.trim()); toast.success(`Test email ${message.status.toLowerCase()}`); }
    catch (cause) { toast.error(cause instanceof ApiClientError ? cause.message : "Unable to send test."); }
    finally { setTestBusy(false); }
  }

  async function schedule() {
    if (!campaign || !scheduleLocal) return;
    const at = new Date(scheduleLocal);
    if (Number.isNaN(at.getTime()) || at <= new Date()) {
      toast.error("Choose a future schedule time.");
      return;
    }
    const request = () => campaign.status === "SCHEDULED"
      ? rescheduleCampaign(campaign.id, at.toISOString(), Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC")
      : scheduleCampaign(campaign.id, at.toISOString(), Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC");
    await runAction(request, campaign.status === "SCHEDULED" ? "Campaign rescheduled" : "Campaign scheduled");
  }

  const canSchedule = Boolean(campaign && (campaign.status === "DRAFT" || campaign.status === "SCHEDULED"));
  const canSendNow = Boolean(campaign && campaign.status === "DRAFT" && (report?.total ?? 0) > 0);
  const currentContacts = contactsPage?.items ?? [];
  const selectedTemplate = templates.find((template) => template.id === draft.templateId);
  const activeTemplateVersion = templateVersion?.templateId === draft.templateId ? templateVersion : null;
  const templateError = templateErrorState && templateErrorState.id === (draft.templateId ?? "list")
    ? templateErrorState.message
    : null;
  const templateLoading = Boolean(selectedTemplate?.currentVersion && !activeTemplateVersion && !templateError);
  const eligiblePageContacts = currentContacts.filter(
    (contact) => contact.status === "ACTIVE" && contact.subscriptionStatus === "SUBSCRIBED",
  );

  if (initialLoading) return <LoadingState label="Loading campaign" />;
  if (initialCampaignId && !campaign && error) return <ErrorState description={error} />;

  return (
    <div className="space-y-5">
      {error ? <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{error}</div> : null}

      {!campaign || editing ? (
        <form className="space-y-4" onSubmit={(event) => { event.preventDefault(); void createDraft(); }}>
          <SectionPanel title="Email content">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5 md:col-span-2">
                <Label htmlFor="campaign-name">Campaign name</Label>
                <Input id="campaign-name" value={draft.name} maxLength={160} required onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} />
              </div>
              <div className="space-y-1.5 md:col-span-2">
                <Label htmlFor="campaign-template">Email template</Label>
                <select
                  id="campaign-template"
                  value={draft.templateId ?? ""}
                  disabled={templateLoading}
                  onChange={(event) => setDraft((current) => ({ ...current, templateId: event.target.value || undefined }))}
                  className="h-10 w-full rounded-lg border border-input bg-card px-3 text-sm text-foreground"
                >
                  <option value="">Compose inline</option>
                  {templates.filter((template) => template.status === "ACTIVE" && template.currentVersion).map((template) => (
                    <option value={template.id} key={template.id}>{template.name} Ãƒâ€šÃ‚· v{template.currentVersion}</option>
                  ))}
                </select>
                {templateError ? <p role="alert" className="text-sm text-destructive">{templateError}</p> : null}
                {!templateError && templates.length === 0 ? <p className="text-xs text-muted-foreground">No active versioned templates yet. Inline content remains available.</p> : null}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="campaign-sender">Verified sender</Label>
                <Input id="campaign-sender" type="email" value={draft.sender} required onChange={(event) => setDraft((current) => ({ ...current, sender: event.target.value }))} placeholder="team@example.com" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="campaign-reply-to">Reply-to</Label>
                <Input id="campaign-reply-to" type="email" value={draft.replyTo} onChange={(event) => setDraft((current) => ({ ...current, replyTo: event.target.value }))} placeholder="Optional" />
              </div>
              {draft.templateId ? (
                <div className="space-y-3 md:col-span-2">
                  <h3 className="text-sm font-semibold">Template version preview</h3>
                  {templateLoading ? <LoadingState label="Loading template" /> : null}
                  {templateVersion ? (
                    <div className="space-y-3 rounded-lg border bg-muted/20 p-3">
                      <p className="text-sm"><span className="text-muted-foreground">Subject</span><br /><strong>{templateVersion.subject}</strong></p>
                      <div className="space-y-1.5">
                        <Label htmlFor="campaign-template-html">HTML</Label>
                        <Textarea id="campaign-template-html" value={templateVersion.htmlContent} readOnly className="min-h-32 font-mono text-xs" />
                      </div>
                      {templateVersion.textContent ? <div className="space-y-1.5"><Label htmlFor="campaign-template-text">Plain text</Label><Textarea id="campaign-template-text" value={templateVersion.textContent} readOnly className="min-h-20 font-mono text-xs" /></div> : null}
                      <div className="flex flex-wrap gap-2">
                        {Object.keys(templateVersion.variablesSchema).map((variable) => <code className="rounded border bg-card px-2 py-1 text-xs" key={variable}>{`{{${variable}}}`}</code>)}
                        {Object.keys(templateVersion.variablesSchema).length === 0 ? <span className="text-xs text-muted-foreground">No merge variables declared</span> : null}
                      </div>
                    </div>
                  ) : null}
                </div>
              ) : (
                <div className="space-y-1.5 md:col-span-2">
                  <Label htmlFor="campaign-subject">Subject</Label>
                  <Input id="campaign-subject" value={draft.subject} maxLength={998} onChange={(event) => setDraft((current) => ({ ...current, subject: event.target.value }))} />
                </div>
              )}
              <div className="space-y-1.5 md:col-span-2">
                <Label htmlFor="campaign-preheader">Preheader</Label>
                <Input id="campaign-preheader" value={draft.preheader} onChange={(event) => setDraft((current) => ({ ...current, preheader: event.target.value }))} />
              </div>
              {!draft.templateId ? (
                <>
                  <div className="space-y-1.5 md:col-span-2">
                    <Label htmlFor="campaign-html">HTML body</Label>
                    <Textarea id="campaign-html" value={draft.htmlBody} className="min-h-40 font-mono text-xs" onChange={(event) => setDraft((current) => ({ ...current, htmlBody: event.target.value }))} placeholder="<p>Hello {{first_name}}</p>" />
                  </div>
                  <div className="space-y-1.5 md:col-span-2">
                    <Label htmlFor="campaign-text">Plain-text body</Label>
                    <Textarea id="campaign-text" value={draft.textBody} className="min-h-28" onChange={(event) => setDraft((current) => ({ ...current, textBody: event.target.value }))} />
                  </div>
                </>
              ) : null}
            </div>
          </SectionPanel>
          <AttachmentPicker value={draft.attachmentIds} onBusyChange={setAttachmentBusy} onChange={(attachmentIds) => setDraft((current) => ({ ...current, attachmentIds }))} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => { if (campaign) setEditing(false); else window.history.back(); }}>Cancel</Button>
            <Button type="submit" loading={saving} disabled={!draft.name.trim() || attachmentBusy}>Save draft</Button>
          </div>
        </form>
      ) : (
        <div className="space-y-5">
          <SectionPanel title={campaign.name} description={`Status: ${statusLabel(campaign.status)}`}>
            {campaign.status === "DRAFT" ? <div className="mb-4 flex justify-end"><Button variant="outline" size="sm" onClick={() => setEditing(true)}>Edit content</Button></div> : null}
            <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <p><span className="text-muted-foreground">Sender</span><br />{campaign.sender || "-"}</p>
              <p><span className="text-muted-foreground">Subject</span><br />{campaign.subject || "Template subject"}</p>
              <p><span className="text-muted-foreground">Recipients</span><br />{report?.total ?? 0}</p>
              <p><span className="text-muted-foreground">Updated</span><br />{new Date(campaign.updatedAt).toLocaleString()}</p>
            </div>
          </SectionPanel>

          {campaign.status === "DRAFT" ? (
            <SectionPanel title="Recipients" description="Only active, subscribed contacts can be added. Suppression is checked again during send acceptance.">
              <div className="space-y-3">
                <div className="rounded-lg border bg-muted/20 p-4 space-y-2">
                  <Label htmlFor="campaign-audience">Bulk audience</Label>
                  <div className="flex flex-wrap gap-2">
                    <select id="campaign-audience" value={audience} onChange={(event) => setAudience(event.target.value)} className="h-10 min-w-48 flex-1 rounded-md border bg-card px-3 text-sm">
                      <option value="">Choose an audience</option>
                      <option value="__all">All subscribed contacts</option>
                      {segments.map((segment) => <option key={segment.id} value={segment.id}>{segment.name}</option>)}
                    </select>
                    <Button variant="outline" disabled={!audience || audienceBusy} loading={audienceBusy} onClick={() => void reviewAudience()}>Review audience</Button>
                  </div>
                  <p className="text-xs text-muted-foreground">Add the entire matching audience. Existing recipients and suppressed contacts are excluded automatically.</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Input aria-label="Search contacts" placeholder="Search contacts" value={contactSearch} onChange={(event) => { setContactSearch(event.target.value); setContactPageNumber(0); }} className="min-w-56 flex-1" />
                  <span className="text-sm text-muted-foreground">{selectedIds.length} selected</span>
                  <Button size="sm" disabled={selectedIds.length === 0 || addingRecipients} loading={addingRecipients} onClick={() => void addSelectedRecipients()}>Add selected</Button>
                </div>
                {contactError ? <ErrorState description={contactError} onRetry={() => void loadContacts()} /> : null}
                {contactsLoading ? <LoadingState label="Loading contacts" /> : null}
                {!contactsLoading && !contactError && currentContacts.length === 0 ? (
                  <EmptyState icon={<Users className="size-5" />} title="No matching contacts" description="Create or import contacts before building the campaign audience." />
                ) : null}
                {!contactsLoading && currentContacts.length > 0 ? (
                  <div className="overflow-x-auto rounded-lg border">
                    <Table>
                      <TableHeader><TableRow><TableHead className="w-12"><input aria-label="Select eligible contacts on this page" type="checkbox" checked={eligiblePageContacts.length > 0 && eligiblePageContacts.every((contact) => selectedIds.includes(contact.id))} onChange={() => togglePage(currentContacts)} /></TableHead><TableHead>Contact</TableHead><TableHead>Company</TableHead><TableHead>Subscription</TableHead></TableRow></TableHeader>
                      <TableBody>
                        {currentContacts.map((contact) => {
                          const eligible = contact.status === "ACTIVE" && contact.subscriptionStatus === "SUBSCRIBED";
                          return (
                            <TableRow key={contact.id}>
                              <TableCell><input aria-label={`Select ${contact.email}`} type="checkbox" disabled={!eligible} checked={selectedIds.includes(contact.id)} onChange={() => toggleContact(contact.id)} /></TableCell>
                              <TableCell><span className="font-medium">{[contact.firstName, contact.lastName].filter(Boolean).join(" ") || contact.email}</span><br /><span className="font-mono text-xs text-muted-foreground">{contact.email}</span></TableCell>
                              <TableCell>{contact.company || "-"}</TableCell>
                              <TableCell>{contact.subscriptionStatus}</TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </div>
                ) : null}
                {contactsPage ? (
                  <div className="flex items-center justify-end gap-3 text-sm text-muted-foreground">
                    <span>Page {contactsPage.page + 1} of {Math.max(1, contactsPage.totalPages)}</span>
                    <Button size="icon" variant="outline" aria-label="Previous contacts" disabled={contactsPage.page <= 0 || contactsLoading} onClick={() => setContactPageNumber((page) => Math.max(0, page - 1))}><ChevronLeft className="size-4" /></Button>
                    <Button size="icon" variant="outline" aria-label="Next contacts" disabled={contactsPage.page + 1 >= contactsPage.totalPages || contactsLoading} onClick={() => setContactPageNumber((page) => page + 1)}><ChevronRight className="size-4" /></Button>
                  </div>
                ) : null}
              </div>
            </SectionPanel>
          ) : null}

          {(report?.total ?? 0) > 0 ? <SectionPanel title="Added recipients" description="Review the saved audience before sending.">
            {addedError ? <ErrorState description={addedError} /> : null}
            {addedAudience ? <><Table><TableHeader><TableRow><TableHead>Email</TableHead><TableHead>Status</TableHead>{campaign.status === "DRAFT" ? <TableHead className="w-24">Actions</TableHead> : null}</TableRow></TableHeader><TableBody>
              {addedAudience.items.map((recipient) => <TableRow key={recipient.id}><TableCell>{recipient.email}</TableCell><TableCell>{statusLabel(recipient.status)}</TableCell>{campaign.status === "DRAFT" ? <TableCell><Button variant="outline" size="sm" disabled={recipientBusy} onClick={() => void removeRecipient(recipient.id)}>Remove</Button></TableCell> : null}</TableRow>)}
            </TableBody></Table><div className="mt-3 flex items-center justify-end gap-3 text-sm text-muted-foreground"><span>Page {addedAudience.page + 1} of {Math.max(1, addedAudience.totalPages)}</span><Button size="icon" variant="outline" aria-label="Previous added recipients" disabled={addedPage === 0} onClick={() => setAddedPage((page) => page - 1)}><ChevronLeft className="size-4" /></Button><Button size="icon" variant="outline" aria-label="Next added recipients" disabled={addedPage + 1 >= addedAudience.totalPages} onClick={() => setAddedPage((page) => page + 1)}><ChevronRight className="size-4" /></Button></div></> : <LoadingState label="Loading added recipients" />}
          </SectionPanel> : null}

          <SectionPanel title="Campaign actions" description="Send and schedule actions use the server-side campaign dispatcher and email outbox.">
            <div className="flex flex-wrap items-end gap-3">
              <Button variant="outline" disabled={(report?.total ?? 0) === 0} loading={previewBusy} onClick={() => void showPreview()}><Eye className="size-4" />Preview &amp; test</Button>
              {canSendNow ? <Button onClick={() => setConfirmSendOpen(true)}><Send className="size-4" />Send now</Button> : null}
              {canSchedule ? (
                <div className="flex flex-wrap items-end gap-2">
                  <div className="space-y-1.5">
                    <Label htmlFor="schedule-at">{campaign.status === "SCHEDULED" ? "Reschedule for" : "Schedule for"}</Label>
                    <Input id="schedule-at" type="datetime-local" value={scheduleLocal} onChange={(event) => setScheduleLocal(event.target.value)} />
                  </div>
                  <Button variant="outline" loading={actionBusy} disabled={(report?.total ?? 0) === 0} onClick={() => void schedule()}><CalendarClock className="size-4" />{campaign.status === "SCHEDULED" ? "Save schedule" : "Schedule"}</Button>
                </div>
              ) : null}
              {campaign.status === "SCHEDULED" || campaign.status === "QUEUED" || campaign.status === "PROCESSING" ? <Button variant="destructive" loading={actionBusy} onClick={() => void runAction(() => stopCampaign(campaign.id), "Campaign stopped")}>Stop</Button> : null}
              {campaign.status === "STOPPED" ? <Button variant="outline" loading={actionBusy} onClick={() => void runAction(() => resumeCampaign(campaign.id), "Campaign resumed")}><Check className="size-4" />Resume</Button> : null}
              {!canSendNow && !canSchedule && campaign.status !== "STOPPED" && campaign.status !== "QUEUED" && campaign.status !== "PROCESSING" ? <p className="text-sm text-muted-foreground">This campaign cannot be changed in its current state.</p> : null}
            </div>
            {report ? (
              <div className="mt-4 grid grid-cols-2 gap-3 border-t pt-4 text-sm sm:grid-cols-4 lg:grid-cols-5">
                {[["Pending", report.pending], ["Accepted", report.accepted], ["Delivered", report.delivered], ["Bounced", report.bounced], ["Failed", report.failed], ["Suppressed", report.suppressed]].map(([label, value]) => <p key={label}><span className="text-muted-foreground">{label}</span><br /><strong>{value}</strong></p>)}
              </div>
            ) : null}
          </SectionPanel>
        </div>
      )}

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto"><DialogHeader><DialogTitle>Recipient preview &amp; test</DialogTitle><DialogDescription>Rendered using {preview?.recipient}. Test sends use your normal sender verification, limits, and delivery pipeline.</DialogDescription></DialogHeader>
          {preview ? <div className="space-y-4"><p className="text-sm"><strong>Subject:</strong> {preview.subject || "No subject"}</p>
            {preview.html ? <HtmlPreview title="Campaign email preview" html={preview.html} subject={preview.subject ?? undefined} /> : <pre className="whitespace-pre-wrap rounded-lg border p-4 text-sm">{preview.text}</pre>}
            <form onSubmit={(event) => { event.preventDefault(); void sendTest(); }} className="flex flex-wrap items-end gap-2"><div className="min-w-48 flex-1 space-y-1.5"><Label htmlFor="campaign-test-to">Test email address</Label><Input id="campaign-test-to" type="email" required value={testTo} onChange={(event) => setTestTo(event.target.value)} /></div><Button type="submit" loading={testBusy} disabled={!testTo.trim()}>Send test</Button></form>
          </div> : null}
        </DialogContent>
      </Dialog>
      <Dialog open={audienceConfirmOpen} onOpenChange={setAudienceConfirmOpen}>
        <DialogContent><DialogHeader><DialogTitle>Add campaign audience</DialogTitle><DialogDescription>
          {audience === "__all" ? `Up to ${audienceCount ?? 0} contacts will be evaluated.` : `${audienceCount ?? 0} active, subscribed contacts match this segment.`} Only eligible, unsuppressed contacts will be added. The audience is saved as a snapshot now; sending checks subscription and suppression again.
        </DialogDescription></DialogHeader><DialogFooter>
          <Button variant="outline" onClick={() => setAudienceConfirmOpen(false)}>Cancel</Button>
          <Button loading={audienceBusy} onClick={() => void addAudience()}>Add audience</Button>
        </DialogFooter></DialogContent>
      </Dialog>
      <Dialog open={confirmSendOpen} onOpenChange={setConfirmSendOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Send campaign now?</DialogTitle>
            <DialogDescription>This will queue {report?.total ?? 0} recipients. Suppressed addresses are filtered by the email sending pipeline.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmSendOpen(false)}>Cancel</Button>
            <Button loading={actionBusy} disabled={!canSendNow} onClick={() => { setConfirmSendOpen(false); void runAction(() => sendCampaignNow(campaign!.id), "Campaign queued for sending"); }}>Confirm send</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}