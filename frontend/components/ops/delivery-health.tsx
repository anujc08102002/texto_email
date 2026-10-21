"use client";

import { motion, useReducedMotion } from "motion/react";
import { cn } from "@/lib/utils";
import type { PresentationDeliveryBreakdown } from "@/types/presentation";

type AnalyticsRateBreakdown = {
  delivered: number;
  bounced: number;
  complained: number;
  suppressed: number;
};

const DELIVERY_ROWS: Array<{ key: keyof PresentationDeliveryBreakdown; label: string; color: string }> = [
  { key: "delivered", label: "Delivered", color: "bg-primary" },
  { key: "deferred", label: "Deferred", color: "bg-warning" },
  { key: "bounced", label: "Bounced", color: "bg-chart-4" },
  { key: "failed", label: "Failed", color: "bg-destructive" },
];

const RATE_ROWS: Array<{ key: keyof AnalyticsRateBreakdown; label: string; color: string }> = [
  { key: "delivered", label: "Delivered", color: "bg-primary" },
  { key: "bounced", label: "Bounced", color: "bg-chart-4" },
  { key: "complained", label: "Complained", color: "bg-warning" },
  { key: "suppressed", label: "Suppressed", color: "bg-destructive" },
];

export function DeliveryHealth({ breakdown }: { breakdown: PresentationDeliveryBreakdown | AnalyticsRateBreakdown }) {
  const reduceMotion = useReducedMotion();
  const rows = "complained" in breakdown
    ? RATE_ROWS.map((row) => ({ ...row, value: breakdown[row.key] }))
    : DELIVERY_ROWS.map((row) => ({ ...row, value: breakdown[row.key] }));

  return (
    <div className="space-y-3">
      {rows.map((row) => {
        const { value } = row;
        return (
          <div key={row.key} className="grid grid-cols-[4.5rem_1fr_3.25rem] items-center gap-2 sm:grid-cols-[88px_1fr_56px] sm:gap-3">
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
