"use client";

import { cn } from "@/lib/utils";

export function SectionPanel({
  title,
  description,
  action,
  children,
  className,
  elevated = false,
  ambient = false,
}: {
  title?: string;
  description?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  elevated?: boolean;
  ambient?: boolean;
}) {
  return (
    <section
      className={cn(
        "rounded-2xl p-5 sm:p-6",
        elevated ? "panel-elevated" : "panel",
        ambient && "ambient-card",
        className,
      )}
    >
      {(title || action) && (
        <div className="mb-4 flex flex-col gap-2 sm:mb-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            {title ? <h2 className="text-sm font-semibold tracking-tight">{title}</h2> : null}
            {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
          </div>
          {action}
        </div>
      )}
      {children}
    </section>
  );
}
