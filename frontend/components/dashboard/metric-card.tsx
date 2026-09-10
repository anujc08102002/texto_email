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

  return (
    <Card className={cn("overflow-hidden", featured && "ambient-card")}>
      <CardContent className="pt-5">
        <div className="flex items-start justify-between gap-3">
          <p className="text-sm text-muted-foreground">{metric.label}</p>
          <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">{icon}</span>
        </div>
        <p className="mt-3 text-2xl font-semibold tracking-tight tabular-nums">{metric.value}</p>
        <div className="mt-2 flex items-center gap-1 text-xs text-muted-foreground">
          <TrendIcon className="size-3.5 shrink-0" aria-hidden />
          <span>{metric.change}</span>
          <span className="text-muted-foreground/70">· {metric.comparison}</span>
        </div>
      </CardContent>
    </Card>
  );
}
