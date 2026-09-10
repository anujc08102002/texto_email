"use client";

import { motion, useReducedMotion } from "motion/react";
import { cn } from "@/lib/utils";

export function UsageMeter({
  label,
  used,
  limit,
  footer,
  className,
}: {
  label: string;
  used: number;
  limit: number | null;
  footer?: string;
  className?: string;
}) {
  const reduceMotion = useReducedMotion();
  const unlimited = limit == null;
  const pct = unlimited || limit <= 0 ? 0 : Math.min(100, (used / limit) * 100);
  const warning = !unlimited && pct >= 80;

  return (
    <div className={cn("space-y-2", className)}>
      <div className="flex items-end justify-between gap-3">
        <div>
          <p className="tech-label">{label}</p>
          <p className="mt-1 font-mono text-sm tabular-nums">
            {used.toLocaleString()}{" "}
            <span className="text-muted-foreground">/</span>{" "}
            {unlimited ? "∞" : limit.toLocaleString()}
          </p>
        </div>
        <p className={cn("text-sm font-semibold tabular-nums", warning && "text-warning-foreground")}>
          {unlimited ? "Unlimited" : `${pct.toFixed(1)}%`}
        </p>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-muted">
        <motion.div
          className={cn("h-full rounded-full", warning ? "bg-warning" : "bg-primary")}
          initial={reduceMotion ? false : { width: 0 }}
          animate={{ width: unlimited ? "8%" : `${pct}%` }}
          transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
        />
      </div>
      {footer ? <p className="text-xs text-muted-foreground">{footer}</p> : null}
    </div>
  );
}
