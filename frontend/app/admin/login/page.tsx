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
    <main className="grid min-h-dvh lg:grid-cols-2">
      <aside className="bg-rail relative hidden overflow-hidden px-12 py-10 text-sidebar-foreground lg:flex lg:flex-col">
        <div className="pointer-events-none absolute inset-0">
          <div className="absolute -left-16 top-0 h-72 w-72 rounded-full bg-primary/35 blur-3xl" />
        </div>
        <Brand href="/" inverted />
        <div className="relative mt-auto max-w-md pb-6">
          <p className="inline-flex rounded-full border border-white/10 bg-white/6 px-2.5 py-1 text-[10px] font-semibold tracking-[0.16em] uppercase">
            Control plane
          </p>
          <p className="font-display mt-5 text-4xl leading-[1.05] tracking-tight">
            Tenant oversight, billing ops, and platform controls.
          </p>
        </div>
      </aside>
      <div className="bg-app flex min-h-dvh flex-col justify-center px-5 py-10 sm:px-8">
        <div className="mx-auto w-full max-w-[26rem]">
          <div className="mb-8 lg:hidden">
            <Brand href="/admin/login" />
          </div>
          <p className="tech-label text-primary">Platform admin</p>
          <h1 className="font-display mt-2 text-4xl tracking-tight">Sign in</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Separate console for tenant oversight. Tenant workspaces use{" "}
            <Link href="/login" className="text-primary hover:underline">
              workspace sign in
            </Link>
            .
          </p>
          <form onSubmit={onSubmit} className="mt-8 space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Admin email</Label>
              <Input id="email" name="email" type="email" required autoComplete="username" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" name="password" type="password" required autoComplete="current-password" />
            </div>
            <Button type="submit" className="w-full" size="lg" loading={submitting}>
              Enter control plane
            </Button>
          </form>
          {message ? (
            <Alert variant="error" className="mt-4">
              <AlertDescription>{message}</AlertDescription>
            </Alert>
          ) : null}
          <p className="mt-8 text-[11px] text-muted-foreground">
            Credentials are configured via <code className="font-mono">ADMIN_EMAIL</code> /{" "}
            <code className="font-mono">ADMIN_PASSWORD</code> in the frontend environment.
          </p>
        </div>
      </div>
    </main>
  );
}
