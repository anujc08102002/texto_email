"use client";

import { motion, useReducedMotion } from "motion/react";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";
import { cn } from "@/lib/utils";
import type { MetricTrend } from "@/types/presentation";

export function MetricDisplay({
  label,
  value,
  change,
  trend = "flat",
  comparison,
  size = "lg",
  className,
  children,
}: {
  label: string;
  value: string;
  change?: string;
  trend?: MetricTrend;
  comparison?: string;
  size?: "sm" | "md" | "lg" | "hero";
  className?: string;
  children?: React.ReactNode;
}) {
  const reduceMotion = useReducedMotion();
  const TrendIcon = trend === "up" ? ArrowUpRight : trend === "down" ? ArrowDownRight : Minus;

  return (
    <div className={cn("min-w-0", className)}>
      <p className="tech-label">{label}</p>
      <motion.p
        initial={reduceMotion ? false : { opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.22 }}
        className={cn(
          "mt-2 font-semibold tracking-tight text-foreground tabular-nums",
          size === "hero" && "text-5xl sm:text-6xl",
          size === "lg" && "text-4xl",
          size === "md" && "text-2xl",
          size === "sm" && "text-xl",
        )}
      >
        {value}
      </motion.p>
      {(change || comparison) && (
        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          {change ? (
            <span
              className={cn(
                "inline-flex items-center gap-0.5 font-medium",
                trend === "up" && "text-success",
                trend === "down" && "text-destructive",
              )}
            >
              <TrendIcon className="size-3.5" />
              {change}
            </span>
          ) : null}
          {comparison ? <span>{comparison}</span> : null}
        </div>
      )}
      {children}
    </div>
  );
}
