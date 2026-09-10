import { Brand } from "@/components/layout/brand";
import { BarChart3, MailCheck, ShieldCheck, Webhook } from "lucide-react";

const highlights = [
  {
    icon: MailCheck,
    title: "Async delivery pipeline",
    description: "Accepted as QUEUED, delivered via RabbitMQ workers with retries and full status tracking.",
  },
  {
    icon: ShieldCheck,
    title: "Verified sending domains",
    description: "Per-tenant sender authorization with SPF/DKIM records and platform test senders.",
  },
  {
    icon: BarChart3,
    title: "Plans, quotas & billing",
    description: "Entitlement-aware limits, usage metrics, and provider-agnostic subscriptions.",
  },
  {
    icon: Webhook,
    title: "Webhooks & suppressions",
    description: "Signed delivery events and automatic bounce/complaint suppression handling.",
  },
];

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="bg-app relative min-h-screen">
      <div className="mx-auto grid min-h-screen w-full max-w-6xl grid-cols-1 lg:grid-cols-2">
        {/* Brand showcase — hidden on small screens */}
        <aside className="aurora relative hidden flex-col justify-between overflow-hidden p-10 text-[var(--brand-ink)] lg:flex xl:p-12">
          <div className="grid-overlay pointer-events-none absolute inset-0" aria-hidden />
          <div
            className="pointer-events-none absolute -top-24 -left-16 h-72 w-72 rounded-full bg-white/10 blur-3xl"
            aria-hidden
          />

          <div className="relative">
            <Brand className="[&_span]:!text-[var(--brand-ink)] [&_.text-muted-foreground]:!text-white/70" />
          </div>

          <div className="relative max-w-md">
            <span className="tech-label !text-white/70">Email service platform</span>
            <h1 className="mt-3 text-3xl font-semibold leading-[1.12] tracking-[-0.03em] xl:text-[2.6rem]">
              Deliverability infrastructure for modern products.
            </h1>
            <p className="mt-4 text-sm leading-relaxed text-white/75">
              Transactional and bulk email with domain verification, async delivery, suppression lists,
              webhooks, and usage-based billing — all behind one clean API and dashboard.
            </p>

            <ul className="mt-8 space-y-4">
              {highlights.map(({ icon: Icon, title, description }) => (
                <li key={title} className="flex gap-3.5">
                  <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl border border-white/15 bg-white/10 backdrop-blur-sm">
                    <Icon className="size-4.5 text-white" />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-white">{title}</span>
                    <span className="mt-0.5 block text-[13px] leading-relaxed text-white/65">{description}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <div className="relative flex items-center gap-6 text-white/70">
            <div>
              <p className="font-mono text-lg font-semibold text-white tabular-nums">99.9%</p>
              <p className="text-[11px] uppercase tracking-[0.12em]">Pipeline uptime</p>
            </div>
            <div className="h-8 w-px bg-white/15" aria-hidden />
            <div>
              <p className="font-mono text-lg font-semibold text-white tabular-nums">&lt;2s</p>
              <p className="text-[11px] uppercase tracking-[0.12em]">Queue to delivery</p>
            </div>
            <div className="h-8 w-px bg-white/15" aria-hidden />
            <div>
              <p className="font-mono text-lg font-semibold text-white tabular-nums">SOC-ready</p>
              <p className="text-[11px] uppercase tracking-[0.12em]">Security model</p>
            </div>
          </div>
        </aside>

        {/* Form column */}
        <div className="relative flex items-center justify-center px-6 py-12 sm:px-10">
          <div
            className="pointer-events-none absolute top-[-6rem] left-1/2 h-72 w-[36rem] -translate-x-1/2 rounded-full bg-primary/15 blur-3xl lg:hidden"
            aria-hidden
          />
          <div className="relative w-full max-w-sm">
            <div className="mb-8 flex justify-center lg:hidden">
              <Brand />
            </div>
            <div className="panel-elevated rounded-2xl p-8 shine-border">{children}</div>
            <p className="mt-6 text-center text-xs text-muted-foreground">
              Protected by tenant-scoped RBAC · Texto Email Platform
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
