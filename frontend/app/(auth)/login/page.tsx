"use client";

import { Eye, EyeOff, Lock, ShieldCheck, User } from "lucide-react";
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
  const [showPassword, setShowPassword] = useState(false);
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
      <div className="mb-8 text-center">
        <span className="mb-4 inline-flex items-center gap-2 rounded-full border border-emerald-500/25 bg-emerald-50 px-3.5 py-1.5 text-xs font-semibold text-emerald-700"><ShieldCheck className="size-3.5" /> Secure Login</span>
        <h1 className="mb-2 text-3xl font-extrabold tracking-tight text-slate-900">Welcome back</h1>
        <p className="text-sm leading-relaxed text-slate-500">Access your TEXTO dashboard and manage your business emails.</p>
      </div>
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <div className="relative"><User className="pointer-events-none absolute left-3.5 top-1/2 size-4.5 -translate-y-1/2 text-slate-400" /><Input id="email" name="email" type="email" required autoComplete="email" placeholder="Enter your email" className="h-12 rounded-xl border-slate-200 pl-10 focus-visible:border-emerald-500 focus-visible:ring-emerald-500/12" /></div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="password">Password</Label>
          <div className="relative"><Lock className="pointer-events-none absolute left-3.5 top-1/2 size-4.5 -translate-y-1/2 text-slate-400" /><Input id="password" name="password" type={showPassword ? "text" : "password"} required autoComplete="current-password" placeholder="Enter your password" className="h-12 rounded-xl border-slate-200 pl-10 pr-11 focus-visible:border-emerald-500 focus-visible:ring-emerald-500/12" /><button type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? "Hide password" : "Show password"} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-emerald-600">{showPassword ? <EyeOff className="size-4.5" /> : <Eye className="size-4.5" />}</button></div>
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
      <p className="mt-8 text-sm text-muted-foreground">
        Need an account? Contact your Texto administrator — they provision workspaces from the admin console.
      </p>
    </div>
  );
}
