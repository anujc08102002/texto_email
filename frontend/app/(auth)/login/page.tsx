"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ApiClientError } from "@/lib/api";
import { saveSession } from "@/lib/session";
import { loginAccount } from "@/services/auth";

export default function LoginPage() {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setMessage(null);
    try {
      const session = await loginAccount({
        email: String(form.get("email") ?? ""),
        password: String(form.get("password") ?? ""),
      });
      saveSession(session);
      router.push("/dashboard");
    } catch (error) {
      setMessage(error instanceof ApiClientError ? error.message : "Unable to reach the API.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="text-xl font-semibold tracking-tight">Workspace sign in</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Use credentials issued by your platform administrator. Public registration is disabled.
      </p>
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input id="email" name="email" type="email" required autoComplete="email" />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="password">Password</Label>
          <Input id="password" name="password" type="password" required autoComplete="current-password" />
        </div>
        <Button type="submit" className="w-full" loading={submitting}>
          Continue
        </Button>
      </form>
      {message ? (
        <Alert variant="error" className="mt-4">
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      ) : null}
      <p className="mt-6 text-sm text-muted-foreground">
        Need an account? Contact your Texto administrator — they provision workspaces from the admin console.
      </p>
    </div>
  );
}
