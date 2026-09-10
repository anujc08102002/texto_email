"use client";

import { cn } from "@/lib/utils";

const DOT = {
  operational: "bg-success",
  degraded: "bg-warning",
  outage: "bg-destructive",
  unknown: "bg-muted-foreground",
  success: "bg-success",
  warning: "bg-warning",
  error: "bg-destructive",
  info: "bg-info",
} as const;

export function HealthIndicator({
  tone = "operational",
  pulse = false,
  size = "sm",
  className,
  label,
}: {
  tone?: keyof typeof DOT;
  pulse?: boolean;
  size?: "sm" | "md";
  className?: string;
  label?: string;
}) {
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <span className={cn("relative inline-flex", size === "sm" ? "size-1.5" : "size-2")}>
        {pulse ? (
          <span className={cn("absolute inline-flex size-full animate-ping rounded-full opacity-40", DOT[tone])} />
        ) : null}
        <span className={cn("relative inline-flex size-full rounded-full", DOT[tone])} aria-hidden />
      </span>
      {label ? <span className="tech-label !tracking-[0.12em]">{label}</span> : null}
    </span>
  );
}
