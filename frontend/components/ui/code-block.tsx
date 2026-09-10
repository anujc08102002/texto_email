import { cn } from "@/lib/utils";
import { CopyButton } from "@/components/ui/copy-button";

export function CodeBlock({
  code,
  className,
  copyable = true,
}: {
  code: string;
  className?: string;
  copyable?: boolean;
}) {
  return (
    <div className={cn("group relative rounded-lg border border-border/80 bg-muted/40", className)}>
      {copyable ? (
        <div className="absolute top-1.5 right-1.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
          <CopyButton value={code} />
        </div>
      ) : null}
      <pre className="overflow-x-auto p-3 font-mono text-[11px] leading-5 text-foreground/90 whitespace-pre-wrap break-all">
        {code}
      </pre>
    </div>
  );
}
