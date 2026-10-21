import { apiGet, apiPost, apiPatch, apiDeleteEmpty, apiDownload } from "@/lib/api";

export type Campaign = {
	id: string;
	name: string;
	status: string;
	sender: string | null;
	replyTo: string | null;
	subject: string | null;
	preheader: string | null;
	templateId: string | null;
	scheduledAt: string | null;
	timezone: string | null;
	createdAt: string;
	updatedAt: string;
};

export type CampaignPage = {
	items: Campaign[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
};

export type CampaignRecipientPage = {
	items: CampaignRecipient[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
};

export type CampaignDraftInput = {
    attachmentIds?: string[];
	name: string;
	sender?: string;
	replyTo?: string;
	subject?: string;
	preheader?: string;
	htmlBody?: string;
	textBody?: string;
	templateId?: string;
};

export type CampaignRecipient = {
	id: string;
	contactId: string | null;
	email: string;
	status: string;
	variables: string;
	errorMessage: string | null;
	createdAt: string;
};

export type CampaignReport = {
	campaignId: string;
	total: number;
	pending: number;
	queued: number;
	accepted: number;
	delivered: number;
	bounced: number;
	complained: number;
	failed: number;
	suppressed: number;
};

export const listCampaigns = (page = 0, size = 20, query = "", status = "", sort = "updatedAt", direction = "desc") => {
	const params = new URLSearchParams({ page: String(page), size: String(size), sort, direction });
	if (query.trim()) params.set("q", query.trim());
	if (status) params.set("status", status);
	return apiGet<CampaignPage>(`/api/v1/campaigns?${params.toString()}`);
};
export const getCampaign = (id: string) => apiGet<Campaign>(`/api/v1/campaigns/${id}`);
export const saveCampaignDraft = (draft: CampaignDraftInput) =>
	apiPost<Campaign>("/api/v1/campaigns/drafts", draft);
export const addCampaignRecipients = (id: string, contactIds: string[]) =>
	apiPost<CampaignRecipient[]>(`/api/v1/campaigns/${id}/recipients`, { contactIds });
export const listCampaignRecipients = (id: string, page = 0, size = 50) =>
	apiGet<CampaignRecipientPage>(`/api/v1/campaigns/${id}/recipients?page=${page}&size=${size}`);
export const getCampaignReport = (id: string) =>
	apiGet<CampaignReport>(`/api/v1/campaigns/${id}/report`);
export const sendCampaignNow = (id: string) =>
	apiPost<Campaign>(`/api/v1/campaigns/${id}/send`);
export const scheduleCampaign = (id: string, scheduledAt: string, timezone: string) =>
	apiPost<Campaign>(`/api/v1/campaigns/${id}/schedule`, { scheduledAt, timezone });
export const rescheduleCampaign = (id: string, scheduledAt: string, timezone: string) =>
	apiPost<Campaign>(`/api/v1/campaigns/${id}/reschedule`, { scheduledAt, timezone });
export const stopCampaign = (id: string) =>
	apiPost<Campaign>(`/api/v1/campaigns/${id}/stop`);
export const resumeCampaign = (id: string) =>
	apiPost<Campaign>(`/api/v1/campaigns/${id}/resume`);

export const getCampaignContent = (id: string) => apiGet<CampaignDraftInput>(`/api/v1/campaigns/${id}/content`);
export const updateCampaignDraft = (id: string, draft: CampaignDraftInput) => apiPatch<Campaign>(`/api/v1/campaigns/${id}`, draft);
export const addCampaignAudience = (id: string, audience: { segmentId?: string; allSubscribed?: boolean }) =>
  apiPost<{ added: number }>(`/api/v1/campaigns/${id}/audience`, { allSubscribed: false, ...audience });

export type CampaignPreview = { recipient: string; sender: string | null; replyTo: string | null; subject: string | null; html: string | null; text: string | null };
export const previewCampaign = (id: string, recipientId?: string) => apiGet<CampaignPreview>(`/api/v1/campaigns/${id}/preview${recipientId ? `?recipientId=${encodeURIComponent(recipientId)}` : ""}`);
export const testCampaign = (id: string, to: string, recipientId?: string) => apiPost<{ id: string; status: string }>(`/api/v1/campaigns/${id}/test`, { to, recipientId });

export const cloneCampaign = (id: string) => apiPost<Campaign>(`/api/v1/campaigns/${id}/clone`);
export const removeCampaignRecipient = (id: string, recipientId: string) => apiDeleteEmpty(`/api/v1/campaigns/${id}/recipients/${recipientId}`);

export const exportCampaignReport = (id: string) => apiDownload(`/api/v1/campaigns/${id}/export`);
