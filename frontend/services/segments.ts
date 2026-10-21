import { apiDeleteEmpty, apiGet, apiPatch, apiPost } from "@/lib/api";
import type { Contact } from "@/services/contacts";

export type Segment = {
	id: string;
	name: string;
	definition: string;
	createdAt: string;
	updatedAt: string;
};

export type SegmentPreview = {
	items: Contact[];
	totalElements: number;
	page: number;
	size: number;
	totalPages: number;
};

export const listSegments = () => apiGet<Segment[]>("/api/v1/segments");
export const createSegment = (name: string, definition: string) =>
	apiPost<Segment>("/api/v1/segments", { name, definition });
export const updateSegment = (id: string, name: string, definition: string) =>
	apiPatch<Segment>(`/api/v1/segments/${id}`, { name, definition });
export const deleteSegment = (id: string) =>
	apiDeleteEmpty(`/api/v1/segments/${id}`);
export const previewSegment = (definition: string, page = 0, size = 10) =>
	apiPost<SegmentPreview>("/api/v1/segments/preview", { definition, page, size });
export const previewSavedSegment = (id: string, page = 0, size = 10) =>
	apiPost<SegmentPreview>(`/api/v1/segments/${id}/preview?page=${page}&size=${size}`);
