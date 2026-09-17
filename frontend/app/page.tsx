import { ArrowRight, BarChart3, Globe, KeyRound, Mail, ShieldCheck, Sparkles, Zap } from "lucide-react";
import Link from "next/link";
import { Brand } from "@/components/layout/brand";
import { ProductPreview } from "@/components/marketing/product-preview";
import { Button } from "@/components/ui/button";
import { getPlatformStatus } from "@/services/platform";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  let statusLabel = "API unreachable";
  let healthy = false;
  try {
    const status = await getPlatformStatus();
    statusLabel = `${status.name} ${status.version} · ${status.status}`;
    healthy = status.status === "ok";
  } catch {
    statusLabel = "Backend is not reachable on NEXT_PUBLIC_API_BASE_URL";
  }

  return (
    <main className="bg-app relative min-h-dvh overflow-x-hidden">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -top-40 left-[-12%] h-[34rem] w-[34rem] rounded-full bg-primary/16 blur-3xl" />
        <div className="absolute top-24 right-[-8%] h-80 w-80 rounded-full bg-warning/20 blur-3xl" />
      </div>

      <header className="relative flex w-full items-center justify-between gap-3 px-4 py-4 sm:px-8 sm:py-5 lg:px-12">
        <Brand href="/" />
        <nav className="flex items-center gap-1 sm:gap-2">
          <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
            <Link href="/plans">Plans</Link>
          </Button>
          <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
            <Link href="/admin/login">Admin</Link>
          </Button>
          <Button asChild size="sm">
            <Link href="/login">Sign in</Link>
          </Button>
        </nav>
      </header>

      <div className="relative grid w-full items-center gap-10 px-4 pb-10 pt-4 sm:px-8 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-12 lg:px-12 lg:pb-16 lg:pt-8">
        <div className="min-w-0">
          <div className="inline-flex max-w-full items-center gap-2 rounded-full border border-border/80 bg-card/80 px-3 py-1 text-xs shadow-xs backdrop-blur-sm">
            <span className={`size-1.5 shrink-0 rounded-full ${healthy ? "bg-success" : "bg-destructive"}`} aria-hidden />
            <span className="text-tech truncate text-muted-foreground">{statusLabel}</span>
          </div>
          <h1 className="text-display mt-6 text-foreground sm:mt-7">
            Email that
            <span className="mt-1 block italic text-primary">ships with the product.</span>
          </h1>
          <p className="text-body mt-5 max-w-xl text-muted-foreground sm:mt-6">
            Texto is transactional infrastructure for teams who care about delivery, domains, and developer access —
            not another marketing blast tool.
          </p>
          <div className="mt-7 flex flex-wrap gap-3 sm:mt-8">
            <Button asChild size="lg">
              <Link href="/login">
                Open workspace
                <ArrowRight />
              </Link>
            </Button>
            <Button asChild variant="secondary" size="lg">
              <Link href="/plans">View plans</Link>
            </Button>
          </div>
          <dl className="mt-8 grid max-w-lg grid-cols-3 gap-3 border-t border-border/70 pt-5 sm:mt-10 sm:gap-4 sm:pt-6">
            {[
              { label: "Delivery", value: "Async" },
              { label: "Auth", value: "SPF · DKIM" },
              { label: "Access", value: "Keys once" },
            ].map((item) => (
              <div key={item.label}>
                <dt className="tech-label">{item.label}</dt>
                <dd className="mt-1 text-sm font-medium">{item.value}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="min-w-0 lg:block">
          <ProductPreview />
        </div>
      </div>

      <section className="relative w-full px-4 pb-16 sm:px-8 sm:pb-20 lg:px-12">
        <p className="tech-label">Platform</p>
        <h2 className="font-display mt-2 text-3xl tracking-tight text-foreground">Everything after “send”.</h2>
        <ul className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {[
            { icon: Mail, title: "Message control", body: "Compose, inspect status, and follow delivery timelines." },
            { icon: Globe, title: "Domain readiness", body: "SPF, DKIM, and DMARC without leaving the workspace." },
            { icon: KeyRound, title: "Developer access", body: "API keys revealed once. Secrets stay masked." },
            { icon: BarChart3, title: "Usage & plans", body: "Live entitlements, quotas, and billing context." },
            { icon: Zap, title: "Async pipeline", body: "Queue, process, send — never block the product request." },
            { icon: ShieldCheck, title: "Suppressions", body: "Keep bounced and complained addresses out of the path." },
            { icon: Sparkles, title: "Templates", body: "Versioned HTML for product and transactional mail." },
            { icon: ArrowRight, title: "Webhooks", body: "Push delivery events into your own systems." },
          ].map((item) => (
            <li key={item.title} className="panel group rounded-2xl p-5 transition-transform duration-150 hover:-translate-y-0.5">
              <item.icon className="size-4 text-primary" aria-hidden />
              <p className="mt-4 text-sm font-semibold">{item.title}</p>
              <p className="text-caption mt-1.5 leading-5">{item.body}</p>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
