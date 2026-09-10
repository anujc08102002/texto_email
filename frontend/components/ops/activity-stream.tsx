"use client";

import { HealthIndicator } from "@/components/ops/health-indicator";
import type { PresentationStreamEvent } from "@/types/presentation";
import { cn } from "@/lib/utils";

const STATUS_TONE = {
  DELIVERED: "operational",
  SENDING: "info",
  PROCESSING: "info",
  QUEUED: "info",
  DEFERRED: "degraded",
  FAILED: "outage",
} as const;

export function ActivityStream({ events }: { events: PresentationStreamEvent[] }) {
  return (
    <div className="space-y-0">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <HealthIndicator tone="operational" pulse />
          <span className="tech-label text-primary">Live</span>
        </div>
        <p className="text-[11px] text-muted-foreground">UI-ready for event stream</p>
      </div>
      <ul className="divide-y divide-border/70">
        {events.map((event) => (
          <li key={event.id} className="grid grid-cols-[auto_1fr_auto] items-start gap-3 py-2.5 first:pt-0 last:pb-0">
            <HealthIndicator
              tone={STATUS_TONE[event.status]}
              pulse={event.status === "SENDING" || event.status === "PROCESSING"}
              className="mt-1"
            />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-medium">{event.status}</span>
                <span className="truncate font-mono text-[11px] text-muted-foreground">{event.subject}</span>
              </div>
              <p className="mt-0.5 truncate text-xs text-muted-foreground">{event.recipient}</p>
              {event.detail ? (
                <p className="mt-1 font-mono text-[11px] text-warning-foreground">{event.detail}</p>
              ) : null}
            </div>
            <div className="text-right">
              <p className="font-mono text-[11px] tabular-nums text-muted-foreground">{event.timestamp}</p>
              <p className={cn("mt-0.5 font-mono text-[11px] tabular-nums", event.latencyMs == null && "text-muted-foreground/60")}>
                {event.latencyMs == null ? "—" : `${event.latencyMs}ms`}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
