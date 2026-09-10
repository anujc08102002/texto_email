"use client";

import { motion, useReducedMotion } from "motion/react";
import { cn } from "@/lib/utils";
import type { PresentationDeliveryBreakdown } from "@/types/presentation";

const ROWS: Array<{ key: keyof PresentationDeliveryBreakdown; label: string; color: string }> = [
  { key: "delivered", label: "Delivered", color: "bg-primary" },
  { key: "deferred", label: "Deferred", color: "bg-warning" },
  { key: "bounced", label: "Bounced", color: "bg-chart-4" },
  { key: "failed", label: "Failed", color: "bg-destructive" },
];

export function DeliveryHealth({ breakdown }: { breakdown: PresentationDeliveryBreakdown }) {
  const reduceMotion = useReducedMotion();

  return (
    <div className="space-y-3">
      {ROWS.map((row) => {
        const value = breakdown[row.key];
        return (
          <div key={row.key} className="grid grid-cols-[88px_1fr_56px] items-center gap-3">
            <p className="text-xs text-muted-foreground">{row.label}</p>
            <div className="h-1.5 overflow-hidden rounded-full bg-muted">
              <motion.div
                className={cn("h-full rounded-full", row.color)}
                initial={reduceMotion ? false : { width: 0 }}
                animate={{ width: `${Math.min(100, value)}%` }}
                transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
              />
            </div>
            <p className="text-right font-mono text-xs tabular-nums">{value.toFixed(2)}%</p>
          </div>
        );
      })}
    </div>
  );
}
