"use client";

import { useMemo, useState } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { SectionPanel } from "@/components/ops/section-panel";
import { ANALYTICS_SERIES, WEEKLY_SERIES } from "@/lib/presentation";
import { cn } from "@/lib/utils";

const RANGES = [
  { id: "24h", label: "24H" },
  { id: "7d", label: "7D" },
  { id: "30d", label: "30D" },
  { id: "90d", label: "90D" },
] as const;

export function TrafficChart() {
  const [range, setRange] = useState<(typeof RANGES)[number]["id"]>("24h");
  const data = useMemo(() => (range === "24h" ? ANALYTICS_SERIES : WEEKLY_SERIES), [range]);

  return (
    <SectionPanel
      title="Email traffic"
      description="Sent · Delivered · Deferred · Bounced"
      action={
        <div className="flex flex-wrap rounded-lg border border-border/70 bg-background/50 p-0.5">
          {RANGES.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setRange(item.id)}
              className={cn(
                "rounded-md px-2.5 py-1 text-[11px] font-medium transition-colors",
                range === item.id ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
      }
      className="flex min-h-[280px] flex-1 flex-col"
    >
      <div className="h-64 min-h-[240px] flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 4, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="trafficSent" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.28} />
                <stop offset="100%" stopColor="var(--primary)" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="var(--border)" vertical={false} strokeOpacity={0.7} />
            <XAxis dataKey="date" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} axisLine={false} tickLine={false} width={40} />
            <Tooltip
              contentStyle={{
                background: "var(--popover)",
                border: "1px solid var(--border)",
                borderRadius: 10,
                fontSize: 12,
                boxShadow: "var(--shadow-sm)",
              }}
            />
            <Area type="monotone" dataKey="sent" name="Sent" stroke="var(--primary)" fill="url(#trafficSent)" strokeWidth={2} />
            <Area type="monotone" dataKey="delivered" name="Delivered" stroke="var(--chart-2)" fill="none" strokeWidth={1.5} />
            <Area type="monotone" dataKey="deferred" name="Deferred" stroke="var(--chart-4)" fill="none" strokeWidth={1.25} />
            <Area type="monotone" dataKey="bounced" name="Bounced" stroke="var(--chart-5)" fill="none" strokeWidth={1.25} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-2 text-[11px] text-muted-foreground">
        Preview series until analytics API is connected.
      </p>
    </SectionPanel>
  );
}
