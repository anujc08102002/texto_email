"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { ContactImportDialog } from "@/components/contacts/contact-import-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import DataGrid from "@/components/data-grid/DataGrid";
import type { SortState } from "@/components/data-grid/types";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";

import { ApiClientError } from "@/lib/api";
import {
  createContact,
  updateContact,
  type Contact,
  deleteContact,
  listContacts,
  type ContactInput,
  type ContactPage,
} from "@/services/contacts";

const initial: ContactInput = {
  email: "",
  firstName: "",
  lastName: "",
  company: "",
  phone: "",
  source: "manual",
  properties: "{}",
};

export function ContactsWorkspace() {
  const requestId = useRef(0);
  const [data, setData] = useState<ContactPage | null>(null);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ key: "createdAt", direction: "desc" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editing, setEditing] = useState<Contact | null>(null);
  const [form, setForm] = useState<ContactInput>(initial);
  const [saving, setSaving] = useState(false);

  const load = useCallback(
    async (requestedPage = page) => {
      const currentRequest = ++requestId.current;
      setLoading(true);
      setError(null);
      try {
        const result = await listContacts(requestedPage, size, query, sort.key, sort.direction);
        if (currentRequest === requestId.current) setData(result);
      } catch (cause) {
        if (currentRequest !== requestId.current) return;
        setError(
          cause instanceof ApiClientError
            ? cause.message
            : "Unable to load contacts.",
        );
      } finally {
        if (currentRequest === requestId.current) setLoading(false);
      }
    },
    [page, query, size, sort],
  );

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 250);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function create() {
    setSaving(true);
    try {
      if (editing) await updateContact(editing.id, form);
      else await createContact(form);
      toast.success(editing ? "Contact updated" : "Contact created");
      setForm(initial);
      setOpen(false);
      if (page !== 0) {
        setPage(0);
      } else {
        await load(0);
      }
    } catch (cause) {
      toast.error(
        cause instanceof ApiClientError
          ? cause.message
          : "Unable to create contact.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function remove(id: string) {
    try {
      await deleteContact(id);
      toast.success("Contact archived");
      const nextPage = data?.items.length === 1 && page > 0 ? page - 1 : page;
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await load(page);
      }
    } catch (cause) {
      toast.error(
        cause instanceof ApiClientError
          ? cause.message
          : "Unable to delete contact.",
      );
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setImportOpen(true)}>
            Import CSV
          </Button>
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button onClick={() => { setEditing(null); setForm(initial); }}>
                <Plus className="size-4" />
                Add contact
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>{editing ? "Edit contact" : "Add contact"}</DialogTitle>
              </DialogHeader>
              <div className="grid gap-3 sm:grid-cols-2">
                {(
                  [
                    ["email", "Email"],
                    ["firstName", "First name"],
                    ["lastName", "Last name"],
                    ["company", "Company"],
                    ["phone", "Phone"],
                  ] as const
                ).map(([key, label]) => (
                  <div className="space-y-1.5" key={key}>
                    <Label htmlFor={key}>{label}</Label>
                    <Input
                      id={key}
                      disabled={key === "email" && Boolean(editing)}
                      maxLength={key === "email" ? 320 : key === "company" ? 200 : key === "phone" ? 64 : 120}
                      type={key === "email" ? "email" : "text"}
                      value={form[key] ?? ""}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          [key]: event.target.value,
                        }))
                      }
                    />
                  </div>
                ))}
                <div className="space-y-1.5 sm:col-span-2"><Label htmlFor="contact-properties">Custom properties (JSON)</Label><Textarea id="contact-properties" className="min-h-28 font-mono text-xs" value={form.properties ?? "{}"} onChange={(event) => setForm((current) => ({ ...current, properties: event.target.value }))} placeholder={'{"city":"Mumbai","total_spent":1200}'} /><p className="text-xs text-muted-foreground">Use these properties in segment rules and email merge fields.</p></div>
                <div className="space-y-1.5 sm:col-span-2"><Label htmlFor="contact-subscription">Subscription</Label><select id="contact-subscription" className="h-9 w-full rounded-md border bg-card px-2 text-sm" value={form.subscriptionStatus ?? "SUBSCRIBED"} onChange={(event) => setForm((current) => ({ ...current, subscriptionStatus: event.target.value }))}><option value="SUBSCRIBED">Subscribed</option><option value="UNSUBSCRIBED">Unsubscribed</option><option value="SUPPRESSED">Suppressed</option></select></div>
              </div>
              <DialogFooter>
                <Button
                  disabled={!form.email}
                  loading={saving}
                  onClick={() => void create()}
                >
                  {editing ? "Save changes" : "Create contact"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <ContactImportDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onImported={() => {
          if (page !== 0) {
            setPage(0);
          } else {
            void load(0);
          }
        }}
      />

      {error ? <ErrorState description={error} onRetry={() => void load()} /> : null}
      <DataGrid pageName="email-contacts" rows={data?.items ?? []} rowKey={(contact) => contact.id}
        loading={loading} entityName="Contacts" totalCountLabel={data?.totalElements ?? 0}
        searchPlaceholder="Search contacts" emptyTitle="No contacts found" emptyMessage="Add or import contacts, or adjust your search."
        server={{ page, pageSize: size, total: data?.totalElements ?? 0, search: query,
          onPageChange: setPage, onPageSizeChange: (value) => { setSize(value); setPage(0); },
          onSearchChange: (value) => { setQuery(value); setPage(0); }, sort,
          onSortChange: (value) => { setSort(value); setPage(0); } }}
        columns={[
          { key: "email", label: "Contact", fixed: true, sortable: true, render: (contact) => <div><p className="font-medium">{[contact.firstName, contact.lastName].filter(Boolean).join(" ") || contact.email}</p><p className="text-xs text-gray-500">{contact.email}</p></div> },
          { key: "company", label: "Company", sortable: true },
          { key: "subscriptionStatus", label: "Status", sortable: true },
          { key: "source", label: "Source", sortable: true },
          { key: "actions", label: "Actions", fixed: true, render: (contact) => <div className="flex gap-2">
            <Button size="sm" variant="outline" onClick={() => { setEditing(contact); setForm({ email: contact.email, firstName: contact.firstName ?? "", lastName: contact.lastName ?? "", company: contact.company ?? "", phone: contact.phone ?? "", source: contact.source ?? "manual", properties: contact.properties, subscriptionStatus: contact.subscriptionStatus }); setOpen(true); }}>Edit</Button>
            <Button size="sm" variant="secondary" onClick={() => void remove(contact.id)}>Archive</Button>
          </div> },
        ]}
      />
    </div>
  );
}
