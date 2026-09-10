import Link from "next/link";
import { ArrowRight, BarChart3, Globe, KeyRound, Mail } from "lucide-react";
import { Brand } from "@/components/layout/brand";
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
    <main className="bg-app relative min-h-screen overflow-hidden">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -top-32 left-[-8%] h-[28rem] w-[28rem] rounded-full bg-primary/12 blur-3xl" />
        <div className="absolute top-32 right-[-10%] h-72 w-72 rounded-full bg-primary/8 blur-3xl" />
      </div>
      <div className="relative mx-auto flex min-h-screen max-w-6xl flex-col justify-center px-6 py-16">
        <Brand href="/" />
        <div className="mt-10 inline-flex w-fit items-center gap-2 rounded-full border border-border/80 bg-card/90 px-3 py-1 text-xs shadow-xs">
          <span className={`size-1.5 rounded-full ${healthy ? "bg-success" : "bg-destructive"}`} aria-hidden />
          <span className="text-tech text-muted-foreground">{statusLabel}</span>
        </div>
        <h1 className="text-display mt-7 max-w-3xl text-foreground">
          Texto
          <span className="mt-2 block text-[0.55em] font-medium tracking-tight text-muted-foreground sm:text-[0.48em]">
            Premium email infrastructure for products that ship.
          </span>
        </h1>
        <p className="text-body mt-5 max-w-xl text-muted-foreground">
          Multi-tenant transactional delivery, domains, developer keys, and plan entitlements — built as a serious ESP,
          not a marketing toy.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <Button asChild size="lg">
            <Link href="/login">
              Workspace sign in
              <ArrowRight />
            </Link>
          </Button>
          <Button asChild variant="secondary" size="lg">
            <Link href="/plans">View plans</Link>
          </Button>
          <Button asChild variant="ghost" size="lg">
            <Link href="/admin/login">Admin console</Link>
          </Button>
        </div>
        <ul className="mt-14 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { icon: Mail, title: "Message control", body: "Compose, track status, inspect delivery timelines." },
            { icon: Globe, title: "Domain readiness", body: "SPF, DKIM, and DMARC status at a glance." },
            { icon: KeyRound, title: "Developer access", body: "Keys revealed once. Secrets stay masked." },
            { icon: BarChart3, title: "Usage & plans", body: "Live entitlements, quotas, and billing context." },
          ].map((item) => (
            <li key={item.title} className="rounded-lg border border-border/80 bg-card/90 p-4 shadow-xs">
              <item.icon className="size-4 text-primary" aria-hidden />
              <p className="mt-3 text-sm font-semibold">{item.title}</p>
              <p className="text-caption mt-1 leading-5">{item.body}</p>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
