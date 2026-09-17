import type { ReactNode } from "react";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { PresentationMetric } from "@/types/presentation";

const ICONS = {
  up: ArrowUpRight,
  down: ArrowDownRight,
  flat: Minus,
};

export function MetricCard({
  metric,
  icon,
  featured = false,
}: {
  metric: PresentationMetric;
  icon: ReactNode;
  featured?: boolean;
}) {
  const TrendIcon = ICONS[metric.trend];
  const trendTone =
    metric.trend === "up" ? "text-success" : metric.trend === "down" ? "text-destructive" : "text-muted-foreground";

  return (
    <Card className={cn("h-full overflow-hidden hover:translate-y-0", featured && "ambient-card")}>
      <CardContent className="px-3.5 pt-4 pb-4 sm:px-5 sm:pt-5 sm:pb-5">
        <div className="flex items-start justify-between gap-2 sm:gap-3">
          <p className="truncate text-xs text-muted-foreground sm:text-sm">{metric.label}</p>
          <span className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary sm:size-9">
            {icon}
          </span>
        </div>
        <p className="font-display mt-3 text-[1.45rem] leading-none tracking-tight tabular-nums sm:mt-4 sm:text-[2rem]">
          {metric.value}
        </p>
        <div className={cn("mt-2 flex min-w-0 items-center gap-1 text-[11px] sm:mt-3 sm:text-xs", trendTone)}>
          <TrendIcon className="size-3.5 shrink-0" aria-hidden />
          <span className="truncate">{metric.change}</span>
          <span className="hidden truncate text-muted-foreground/80 sm:inline">· {metric.comparison}</span>
        </div>
      </CardContent>
    </Card>
  );
}
