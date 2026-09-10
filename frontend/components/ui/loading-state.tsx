import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

function LoadingState({
  className,
  rows = 4,
  label = "Loading",
}: {
  className?: string;
  rows?: number;
  label?: string;
}) {
  return (
    <div role="status" aria-live="polite" aria-label={label} className={cn("space-y-3", className)}>
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} className="h-12 w-full" />
      ))}
    </div>
  );
}

export { LoadingState };
