import { ContactsWorkspace } from "@/components/contacts/contacts-workspace";
import { PageHeader } from "@/components/layout/page-header";
export default function ContactsPage(){return <div className="flex min-h-full flex-col gap-4"><PageHeader eyebrow="Personalize" title="Contacts" description="Manage your contacts, subscriptions, and custom properties."/><ContactsWorkspace/></div>}
