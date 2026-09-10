import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  className,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("mb-1 flex flex-wrap items-end justify-between gap-4 border-b border-border/60 pb-5", className)}>
      <div className="min-w-0">
        {eyebrow ? <p className="tech-label text-primary">{eyebrow}</p> : null}
        <h1 className={cn("text-page-heading text-foreground", eyebrow && "mt-1")}>{title}</h1>
        {description ? <p className="text-body mt-1 max-w-2xl text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}
