"use client";

import { EmailComposer } from "@/components/emails/email-composer";
import { useStoredUser } from "@/hooks/use-stored-user";

export default function ComposeEmailPage() {
  const user = useStoredUser();
  return <EmailComposer key={user?.email ?? "empty"} defaultTo={user?.email ?? ""} />;
}
