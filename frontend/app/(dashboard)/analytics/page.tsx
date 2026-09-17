"use client";

import { useState } from "react";
import { AnalyticsChart } from "@/components/analytics/analytics-chart";
import { PageHeader } from "@/components/layout/page-header";
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
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Analytics"
        title="Delivery telemetry"
        description="Professional volume and mix. Series are preview until GET /api/v1/analytics."
        actions={
          <div className="flex w-full flex-wrap rounded-full border border-border/70 bg-card p-0.5 sm:w-auto">
            {RANGES.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setRange(item.id)}
                className={cn(
                  "flex-1 rounded-full px-3 py-1.5 text-xs font-medium transition-colors sm:flex-none",
                  range === item.id ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
                )}
              >
                {item.label}
              </button>
            ))}
          </div>
        }
      />

      <p className="text-[11px] text-muted-foreground">{PRESENTATION_NOTICE}</p>

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay label="Sent" value="40,750" change="↑ 8.2%" trend="up" size="md" />
        </SectionPanel>
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay label="Delivered" value="39,911" change="↑ 8.4%" trend="up" size="md" />
        </SectionPanel>
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay label="Open rate" value="—" comparison="Requires engagement API" size="md" />
        </SectionPanel>
        <SectionPanel className="p-4 sm:p-5">
          <MetricDisplay label="Click rate" value="—" comparison="Requires engagement API" size="md" />
        </SectionPanel>
      </div>

      <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,20rem)]">
        <AnalyticsChart
          title="Volume"
          description="Sent · delivered · bounced · failed"
          data={series}
          preview
          fill
          className="min-h-[280px] xl:min-h-0"
        />
        <SectionPanel title="Delivery mix" className="h-fit">
          <DeliveryHealth breakdown={DELIVERY_BREAKDOWN} />
        </SectionPanel>
      </div>
    </div>
  );
}
