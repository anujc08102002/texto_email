import type { ReactNode } from "react";
import { Card, CardContent } from "@/components/ui/card";
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
    <Card className={cn("border-dashed bg-muted/20 shadow-none hover:translate-y-0", className)}>
      <CardContent className="flex flex-col items-start gap-4 py-12">
        {icon ? (
          <div className="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">{icon}</div>
        ) : null}
        <div>
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <p className="mt-1 max-w-lg text-sm leading-6 text-muted-foreground">{description}</p> : null}
        </div>
        {action}
      </CardContent>
    </Card>
  );
}

export { EmptyState };
