"use client";

import { HealthIndicator } from "@/components/ops/health-indicator";
import type { PresentationDomain } from "@/types/presentation";
import { cn } from "@/lib/utils";

function Check({ ok }: { ok: boolean }) {
  return (
    <span className={cn("font-mono text-xs", ok ? "text-success" : "text-warning-foreground")}>
      {ok ? "✓" : "!"}
    </span>
  );
}

export function DomainHealth({ domains }: { domains: PresentationDomain[] }) {
  if (domains.length === 0) {
    return <p className="text-sm text-muted-foreground">No sending domains yet.</p>;
  }

  return (
    <ul className="space-y-3">
      {domains.map((domain) => {
        const healthy = domain.spf === "pass" && domain.dkim === "pass" && domain.dmarc === "pass";
        return (
          <li key={domain.id} className="rounded-lg border border-border/70 bg-background/40 px-3 py-2.5">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <HealthIndicator tone={healthy ? "operational" : "degraded"} />
                <p className="font-mono text-sm">{domain.domain}</p>
              </div>
              {typeof domain.reputation === "number" ? (
                <p className="font-mono text-xs tabular-nums text-muted-foreground">{domain.reputation}/100</p>
              ) : (
                <p className="tech-label">Pending</p>
              )}
            </div>
            <div className="mt-2 flex gap-4 text-xs text-muted-foreground">
              <span>
                SPF <Check ok={domain.spf === "pass"} />
              </span>
              <span>
                DKIM <Check ok={domain.dkim === "pass"} />
              </span>
              <span>
                DMARC <Check ok={domain.dmarc === "pass"} />
              </span>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
