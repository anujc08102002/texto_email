"use client";

import { useState } from "react";
import { AnalyticsChart } from "@/components/analytics/analytics-chart";
import { MetricDisplay } from "@/components/ops/metric-display";
import { SectionPanel } from "@/components/ops/section-panel";
import { DeliveryHealth } from "@/components/ops/delivery-health";
import { ANALYTICS_SERIES, DELIVERY_BREAKDOWN, PRESENTATION_NOTICE, WEEKLY_SERIES } from "@/lib/presentation";
import { cn } from "@/lib/utils";

const RANGES = [
  { id: "7d", label: "7 days" },
  { id: "30d", label: "30 days" },
  { id: "90d", label: "90 days" },
  { id: "custom", label: "Custom" },
] as const;

export default function AnalyticsPage() {
  const [range, setRange] = useState<(typeof RANGES)[number]["id"]>("7d");
  const series = range === "7d" ? WEEKLY_SERIES : ANALYTICS_SERIES;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-border/70 pb-5">
        <div>
          <p className="tech-label text-primary">Analytics</p>
          <h1 className="text-page-heading mt-1">Analytics</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Professional delivery telemetry. Series are preview until GET /api/v1/analytics.
          </p>
        </div>
        <div className="flex rounded-lg border border-border/70 bg-card p-0.5">
          {RANGES.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setRange(item.id)}
              className={cn(
                "rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
                range === item.id ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <p className="text-[11px] text-muted-foreground">{PRESENTATION_NOTICE}</p>

      <div className="grid gap-4 md:grid-cols-4">
        <SectionPanel>
          <MetricDisplay label="Sent" value="40,750" change="↑ 8.2%" trend="up" size="md" />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay label="Delivered" value="39,911" change="↑ 8.4%" trend="up" size="md" />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay label="Open rate" value="—" comparison="Requires engagement API" size="md" />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay label="Click rate" value="—" comparison="Requires engagement API" size="md" />
        </SectionPanel>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.4fr_0.8fr]">
        <AnalyticsChart title="Volume" description="Sent · delivered · bounced · failed" data={series} preview />
        <SectionPanel title="Delivery mix">
          <DeliveryHealth breakdown={DELIVERY_BREAKDOWN} />
        </SectionPanel>
      </div>
    </div>
  );
}
