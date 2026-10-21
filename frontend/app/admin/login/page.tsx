"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ShieldCheck } from "lucide-react";
import { AuthFrame } from "@/components/layout/auth-frame";
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
    <AuthFrame>
          <div className="mb-8 text-center">
            <span className="mb-4 inline-flex items-center gap-2 rounded-full border border-emerald-500/25 bg-emerald-50 px-3.5 py-1.5 text-xs font-semibold text-emerald-700"><ShieldCheck className="size-3.5" /> Secure Admin Login</span>
            <h1 className="mb-2 text-3xl font-extrabold tracking-tight text-slate-900">Welcome back</h1>
            <p className="text-sm leading-relaxed text-slate-500">Manage your TEXTO email platform and tenant workspaces.</p>
          </div>
          <form onSubmit={onSubmit} className="mt-8 space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Admin email</Label>
              <Input id="email" name="email" type="email" required autoComplete="username" placeholder="Enter your admin email" className="h-12 rounded-xl" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" name="password" type="password" required autoComplete="current-password" placeholder="Enter your password" className="h-12 rounded-xl" />
            </div>
            <Button type="submit" className="h-12 w-full rounded-xl font-semibold" size="lg" loading={submitting}>
              Sign in
            </Button>
          </form>
          {message ? (
            <Alert variant="error" className="mt-4">
              <AlertDescription>{message}</AlertDescription>
            </Alert>
          ) : null}
          <p className="mt-8 text-center text-xs text-slate-500">Looking for your workspace? <Link href="/login" className="font-medium text-emerald-600 hover:underline">Workspace sign in</Link></p>
    </AuthFrame>
  );
}
