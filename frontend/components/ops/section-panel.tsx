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
        "rounded-xl p-4 sm:p-5",
        elevated ? "panel-elevated" : "panel",
        ambient && "ambient-green",
        className,
      )}
    >
      {(title || action) && (
        <div className="mb-4 flex items-start justify-between gap-3">
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
