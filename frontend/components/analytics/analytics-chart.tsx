"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { SectionPanel } from "@/components/ops/section-panel";
import { cn } from "@/lib/utils";
import type { PresentationAnalyticsPoint } from "@/types/presentation";

export function AnalyticsChart({
  title,
  description,
  data,
  preview = false,
  className,
  fill = false,
}: {
  title: string;
  description?: string;
  data: PresentationAnalyticsPoint[];
  preview?: boolean;
  className?: string;
  fill?: boolean;
}) {
  return (
    <SectionPanel title={title} description={description} className={cn("min-w-0 overflow-hidden", className)}>
      <div className={cn("min-w-0 overflow-hidden", fill ? "h-64 sm:h-72 lg:h-80" : "h-56 sm:h-64")}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="sentFillAnalytics" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.24} />
                <stop offset="100%" stopColor="var(--primary)" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="var(--border)" vertical={false} />
            <XAxis dataKey="date" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} axisLine={false} tickLine={false} width={36} />
            <Tooltip
              contentStyle={{
                background: "var(--popover)",
                border: "1px solid var(--border)",
                borderRadius: 10,
                fontSize: 12,
              }}
            />
            <Area type="monotone" dataKey="sent" stroke="var(--primary)" fill="url(#sentFillAnalytics)" strokeWidth={2} name="Sent" />
            <Area type="monotone" dataKey="delivered" stroke="var(--chart-2)" fill="none" strokeWidth={1.5} name="Delivered" />
            <Area type="monotone" dataKey="bounced" stroke="var(--chart-4)" fill="none" strokeWidth={1.5} name="Bounced" />
            <Area type="monotone" dataKey="failed" stroke="var(--chart-5)" fill="none" strokeWidth={1.5} name="Failed" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      {preview ? <p className="mt-2 text-[11px] text-muted-foreground">Preview series. Replace with GET /api/v1/analytics.</p> : null}
    </SectionPanel>
  );
}
