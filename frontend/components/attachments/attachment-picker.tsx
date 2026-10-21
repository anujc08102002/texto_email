"use client";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiClientError } from "@/lib/api";
import { getAttachment, uploadAttachment, type AttachmentDocument } from "@/services/attachments";

export function AttachmentPicker({ value = [], onChange, onBusyChange }: { value?: string[]; onChange: (ids: string[]) => void; onBusyChange?: (busy: boolean) => void }) {
  const [documents, setDocuments] = useState<AttachmentDocument[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const key = value.join(",");
  useEffect(() => {
    let cancelled = false;
    Promise.all(key ? key.split(",").map(getAttachment) : []).then((items) => { if (!cancelled) setDocuments(items); })
      .catch(() => { if (!cancelled) setError("Unable to load an attachment. Remove it before sending or try again."); });
    return () => { cancelled = true; };
  }, [key]);
  async function upload(file?: File) {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { setError("Each attachment must be at most 5 MB."); return; }
    if (value.length >= 5) { setError("Choose at most five attachments."); return; }
    if (documents.reduce((total, item) => total + item.sizeBytes, file.size) > 6 * 1024 * 1024) { setError("Combined attachments must be at most 6 MB."); return; }
    setBusy(true); onBusyChange?.(true); setError(null);
    try { const item = await uploadAttachment(file); onChange([...value, item.id]); }
    catch (cause) { setError(cause instanceof ApiClientError ? cause.message : "Unable to upload attachment."); }
    finally { setBusy(false); onBusyChange?.(false); }
  }
  return <div className="space-y-2">
    <Label>Attachments</Label>
    <Input type="file" aria-label="Upload attachment" disabled={busy || value.length >= 5} accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.jpg,.jpeg,.png,.gif,.txt,.zip" onChange={(event) => { void upload(event.target.files?.[0]); event.target.value = ""; }} />
    <p className="text-xs text-muted-foreground">Up to 5 files, 5 MB each and 6 MB combined. Files are included with every recipient&apos;s email.</p>
    {busy ? <p role="status" className="text-xs">Uploading attachment…</p> : null}
    {error ? <p role="alert" className="text-xs text-destructive">{error}</p> : null}
    {value.map((id) => <div className="flex items-center justify-between gap-2 rounded-md border px-3 py-2 text-sm" key={id}>
      <span>{documents.find((item) => item.id === id)?.filename ?? "Attachment"}</span>
      <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={() => { setError(null); onChange(value.filter((current) => current !== id)); }}>Remove</Button>
    </div>)}
  </div>;
}
