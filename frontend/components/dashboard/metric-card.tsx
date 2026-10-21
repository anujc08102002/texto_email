import type { ReactNode } from "react";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";
import { cn } from "@/lib/utils";
import type { PresentationMetric } from "@/types/presentation";

type MetricCardMetric = Pick<PresentationMetric, "id" | "label" | "value"> &
  Partial<Pick<PresentationMetric, "change" | "trend" | "comparison">>;

const ICONS = {
  up: ArrowUpRight,
  down: ArrowDownRight,
  flat: Minus,
};

export function MetricCard({
  metric,
  icon,

}: {
  metric: MetricCardMetric;
  icon: ReactNode;
  featured?: boolean;
}) {
  const TrendIcon = metric.trend ? ICONS[metric.trend] : null;
  const trendTone =
    metric.trend === "up" ? "text-success" : metric.trend === "down" ? "text-destructive" : "text-muted-foreground";

  const tones = {
    sent: { color: "#1d75f2", bg: "#eff6ff" },
    delivery: { color: "#00a65a", bg: "#ecfdf5" },
    bounce: { color: "#f38b00", bg: "#fffbeb" },
    suppression: { color: "#e50914", bg: "#fef2f2" },
  };
  const tone = tones[metric.id as keyof typeof tones] ?? { color: "#059669", bg: "#ecfdf5" };
  return (
    <div className="h-full rounded-xl border-l-4 p-4 text-center transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md" style={{ background: tone.bg, borderLeftColor: tone.color }}>
      <p className="mb-1.5 flex items-center justify-center gap-1.5 text-[11px] font-bold uppercase tracking-wider" style={{ color: tone.color }}>{icon}{metric.label}</p>
      <p className="text-4xl font-extrabold leading-tight tracking-tight tabular-nums" style={{ color: tone.color }}>{metric.value}</p>
      {TrendIcon && metric.change ? <div className={cn("mt-2 flex items-center justify-center gap-1 text-xs", trendTone)}><TrendIcon className="size-3.5" /><span>{metric.change}</span>{metric.comparison ? <span className="text-muted-foreground">· {metric.comparison}</span> : null}</div> : null}
    </div>
  );
}
