import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center rounded-2xl border border-dashed border-border/80 bg-muted/20 px-5 py-10 text-center sm:px-6 sm:py-14",
        className,
      )}
    >
      {icon ? (
        <div className="flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">{icon}</div>
      ) : null}
      <h2 className="font-display mt-4 text-2xl tracking-tight">{title}</h2>
      {description ? <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">{description}</p> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

export { EmptyState };
