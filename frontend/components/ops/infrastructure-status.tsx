"use client";

import { HealthIndicator } from "@/components/ops/health-indicator";
import type { PresentationInfraNode } from "@/types/presentation";
import { cn } from "@/lib/utils";

export function InfrastructureStatus({ nodes }: { nodes: PresentationInfraNode[] }) {
  return (
    <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
      {nodes.map((node) => (
        <div
          key={node.id}
          className="flex items-start justify-between gap-3 rounded-lg border border-border/70 bg-background/50 px-3 py-2.5"
        >
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <HealthIndicator tone={node.status} pulse={node.status === "operational"} />
              <p className="text-sm font-medium">{node.label}</p>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{node.meta}</p>
          </div>
          <div className="text-right">
            <p className="tech-label !normal-case !tracking-normal">
              {node.status === "operational" ? "Operational" : node.status}
            </p>
            {typeof node.latencyMs === "number" ? (
              <p className="mt-1 font-mono text-[11px] tabular-nums text-muted-foreground">{node.latencyMs}ms</p>
            ) : null}
          </div>
        </div>
      ))}
    </div>
  );
}

export function QueueStatus({
  queued,
  processing,
  deferred,
  retrying,
  averageLatencyMs,
}: {
  queued: number;
  processing: number;
  deferred: number;
  retrying: number;
  averageLatencyMs: number;
}) {
  const total = Math.max(queued + processing + deferred + retrying, 1);
  const rows = [
    { label: "Processing", value: processing, color: "bg-primary" },
    { label: "Deferred", value: deferred, color: "bg-warning" },
    { label: "Retrying", value: retrying, color: "bg-chart-5" },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <div>
          <p className="tech-label">Queue</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">{queued.toLocaleString()} queued</p>
        </div>
        <p className="font-mono text-xs text-muted-foreground">avg {averageLatencyMs}ms</p>
      </div>
      <div className="flex h-2 overflow-hidden rounded-full bg-muted">
        {rows.map((row) => (
          <div
            key={row.label}
            className={cn(row.color)}
            style={{ width: `${(row.value / total) * 100}%` }}
            title={`${row.label}: ${row.value}`}
          />
        ))}
      </div>
      <dl className="grid grid-cols-3 gap-3 text-xs">
        {rows.map((row) => (
          <div key={row.label}>
            <dt className="text-muted-foreground">{row.label}</dt>
            <dd className="mt-0.5 font-mono text-sm tabular-nums">{row.value.toLocaleString()}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
