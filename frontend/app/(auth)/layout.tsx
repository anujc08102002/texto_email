import Link from "next/link";
import { Brand } from "@/components/layout/brand";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="grid min-h-dvh lg:grid-cols-2">
      <aside className="bg-rail relative hidden overflow-hidden px-12 py-10 text-sidebar-foreground lg:flex lg:flex-col">
        <div className="pointer-events-none absolute inset-0">
          <div className="absolute -left-24 -top-24 h-80 w-80 rounded-full bg-primary/30 blur-3xl" />
          <div className="absolute bottom-0 right-0 h-64 w-64 rounded-full bg-white/6 blur-3xl" />
        </div>
        <Brand href="/" inverted />
        <div className="relative mt-auto max-w-md pb-6">
          <p className="font-display text-4xl leading-[1.05] tracking-tight">
            Infrastructure for the mail your product actually sends.
          </p>
          <p className="mt-4 text-sm leading-6 text-sidebar-muted">
            Workspaces are provisioned by a platform administrator. Sign in with the credentials issued to your
            organization.
          </p>
          <p className="mt-8 text-xs text-sidebar-muted">
            Prefer the control plane?{" "}
            <Link href="/admin/login" className="text-sidebar-foreground underline-offset-4 hover:underline">
              Admin console
            </Link>
          </p>
        </div>
      </aside>
      <div className="bg-app relative flex min-h-dvh flex-col justify-center px-5 py-10 sm:px-8">
        <div className="mx-auto w-full max-w-[26rem]">
          <div className="mb-8 lg:hidden">
            <Brand href="/" />
          </div>
          {children}
        </div>
      </div>
    </main>
  );
}
