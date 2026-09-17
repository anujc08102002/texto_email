"use client";

import { AdminPreviewBanner } from "@/components/admin/admin-preview-banner";
import { PageHeader } from "@/components/layout/page-header";
import { MetricDisplay } from "@/components/ops/metric-display";
import { SectionPanel } from "@/components/ops/section-panel";
import { DeliveryHealth } from "@/components/ops/delivery-health";
import { TrafficChart } from "@/components/ops/traffic-chart";
import {
  ADMIN_PLATFORM_STATS,
  ADMIN_TENANTS,
} from "@/lib/presentation/admin";
import { DELIVERY_BREAKDOWN } from "@/lib/presentation";

export default function AdminInsightsPage() {
  const highRisk = ADMIN_TENANTS.filter((t) => t.risk === "high").length;
  const frozen = ADMIN_TENANTS.filter((t) => t.sendingFrozen || t.status === "sending_frozen").length;

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Platform admin"
        title="Platform insights"
        description="Fleet-wide delivery, risk, and growth signals across all tenants."
      />
      <AdminPreviewBanner />

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <SectionPanel>
          <MetricDisplay
            label="Active tenants"
            value={ADMIN_PLATFORM_STATS.active.toLocaleString()}
            comparison={`${ADMIN_PLATFORM_STATS.tenants} total`}
            size="md"
          />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay
            label="Delivery rate"
            value={`${ADMIN_PLATFORM_STATS.platformDeliveryRate}%`}
            comparison="Fleet average 24h"
            size="md"
          />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay label="High risk" value={String(highRisk)} comparison={`${frozen} sending frozen`} size="md" />
        </SectionPanel>
        <SectionPanel>
          <MetricDisplay
            label="Messages 24h"
            value={ADMIN_PLATFORM_STATS.messages24h.toLocaleString()}
            comparison="All tenants"
            size="md"
          />
        </SectionPanel>
      </div>

      <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(240px,20rem)]">
        <TrafficChart />
        <SectionPanel title="Fleet delivery mix">
          <DeliveryHealth breakdown={DELIVERY_BREAKDOWN} />
        </SectionPanel>
      </div>
    </div>
  );
}
