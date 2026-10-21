import { apiGet, apiPostForm, apiDownload } from "@/lib/api";
export type AttachmentDocument = { id: string; filename: string; contentType: string; sizeBytes: number; checksumSha256: string; status: string };
export const getAttachment = (id: string) => apiGet<AttachmentDocument>(`/api/v1/attachments/${id}`);
export const uploadAttachment = (file: File) => { const form = new FormData(); form.append("file", file); return apiPostForm<AttachmentDocument>("/api/v1/attachments", form); };
export const downloadAttachment = (id: string) => apiDownload(`/api/v1/attachments/${id}/download`);
