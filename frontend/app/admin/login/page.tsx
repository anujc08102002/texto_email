"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Brand } from "@/components/layout/brand";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAdminSession } from "@/hooks/use-admin-session";
import { useIsClient } from "@/hooks/use-is-client";
import { saveAdminSession, type AdminSession } from "@/lib/admin-session";

export default function AdminLoginPage() {
  const router = useRouter();
  const admin = useAdminSession();
  const isClient = useIsClient();
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isClient && admin) {
      router.replace("/admin");
    }
  }, [admin, isClient, router]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setMessage(null);
    try {
      const response = await fetch("/api/admin/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: String(form.get("email") ?? ""),
          password: String(form.get("password") ?? ""),
        }),
      });
      const payload = (await response.json()) as AdminSession & { error?: string };
      if (!response.ok) {
        throw new Error(payload.error ?? "Unable to sign in.");
      }
      saveAdminSession({ token: payload.token, admin: payload.admin });
      router.push("/admin");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Unable to sign in.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="bg-app relative min-h-screen overflow-hidden">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute top-[-8rem] left-1/2 h-80 w-[40rem] -translate-x-1/2 rounded-full bg-primary/20 blur-3xl" />
      </div>
      <div className="relative mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-12">
        <div className="rounded-2xl border border-border/80 bg-card/80 p-8 shadow-md backdrop-blur-xl">
          <Brand href="/admin/login" />
          <div className="mt-3 inline-flex rounded-md border border-primary/30 bg-primary/10 px-2 py-0.5 text-[10px] font-semibold tracking-[0.14em] text-primary uppercase">
            Platform admin
          </div>
          <div className="mt-6">
            <h1 className="text-xl font-semibold tracking-tight">Admin sign in</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Separate console for tenant oversight. Tenant workspaces use{" "}
              <Link href="/login" className="text-primary hover:underline">
                workspace sign in
              </Link>
              .
            </p>
            <form onSubmit={onSubmit} className="mt-6 space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="email">Admin email</Label>
                <Input id="email" name="email" type="email" required autoComplete="username" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="password">Password</Label>
                <Input id="password" name="password" type="password" required autoComplete="current-password" />
              </div>
              <Button type="submit" className="w-full" loading={submitting}>
                Enter control plane
              </Button>
            </form>
            {message ? (
              <Alert variant="error" className="mt-4">
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            ) : null}
            <p className="mt-6 text-[11px] text-muted-foreground">
              Credentials are configured via <code className="font-mono">ADMIN_EMAIL</code> /{" "}
              <code className="font-mono">ADMIN_PASSWORD</code> in the frontend environment.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
