"use client";

import Link from "next/link";
import { HealthIndicator } from "@/components/ops/health-indicator";
import type { PresentationAttentionItem } from "@/types/presentation";

export function AttentionPanel({ items }: { items: PresentationAttentionItem[] }) {
  if (items.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-success/20 bg-success/5 px-3 py-3">
        <HealthIndicator tone="operational" pulse />
        <p className="text-sm">Everything looks good.</p>
      </div>
    );
  }

  return (
    <ul className="space-y-2">
      {items.map((item) => (
        <li key={item.id}>
          <Link
            href={item.href ?? "#"}
            className="block rounded-lg border border-border/70 bg-background/40 px-3 py-2.5 transition-colors hover:bg-muted/60"
          >
            <div className="flex items-center gap-2">
              <HealthIndicator
                tone={item.severity === "error" ? "outage" : item.severity === "warning" ? "degraded" : "info"}
              />
              <p className="text-sm font-medium">{item.title}</p>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{item.description}</p>
          </Link>
        </li>
      ))}
    </ul>
  );
}
