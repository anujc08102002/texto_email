import { apiDeleteEmpty, apiGet, apiPatch, apiPost, apiPostForm } from "@/lib/api";

export type Contact = { id: string; email: string; firstName: string | null; lastName: string | null; company: string | null; phone: string | null; status: string; subscriptionStatus: string; source: string | null; properties: string; createdAt: string; updatedAt: string };
export type ContactPage = { items: Contact[]; page: number; size: number; totalElements: number; totalPages: number };
export type ContactInput = { email: string; firstName?: string; lastName?: string; company?: string; phone?: string; source?: string; properties?: string; subscriptionStatus?: string };
export type ContactImportResult = { accepted: number; skipped: number; errors: string[] };
export const listContacts=(page=0,size=20,q?:string,sort="createdAt",direction="desc")=>apiGet<ContactPage>(`/api/v1/contacts?page=${page}&size=${size}&sort=${encodeURIComponent(sort)}&direction=${encodeURIComponent(direction)}${q?`&q=${encodeURIComponent(q)}`:""}`);
export const createContact=(input:ContactInput)=>apiPost<Contact>("/api/v1/contacts",input);
export const updateContact=(id:string,input:ContactInput)=>apiPatch<Contact>(`/api/v1/contacts/${id}`,input);
export const deleteContact=(id:string)=>apiDeleteEmpty(`/api/v1/contacts/${id}`);
export const importContactsCsv=(file:File)=>{const form=new FormData();form.append("file",file);return apiPostForm<ContactImportResult>("/api/v1/contacts/import/csv",form)};
