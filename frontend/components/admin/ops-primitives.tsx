import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

export function OpsStatus({ value }: { value: string | number | null | undefined }) {
  const text = String(value ?? "UNKNOWN");
  const normalized = text.toUpperCase();
  const variant = ["DELIVERED", "UP", "ACTIVE", "VERIFIED", "PUBLISHED", "SUCCESS", "2"].some((item) => normalized.startsWith(item))
    ? "success" : ["FAILED", "DOWN", "BOUNCED", "COMPLAINT", "SUSPENDED", "5"].some((item) => normalized.startsWith(item))
      ? "error" : ["QUEUED", "PROCESSING", "SENDING", "PENDING", "DEFERRED", "UNPUBLISHED", "4"].some((item) => normalized.startsWith(item))
        ? "warning" : "secondary";
  return <Badge variant={variant}>{text.replaceAll("_", " ")}</Badge>;
}

export function Mono({ children, className, ...props }: React.ComponentProps<"span">) {
  return <span className={cn("font-mono text-xs", className)} {...props}>{children || "—"}</span>;
}

export function formatOpsDate(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function shortId(value: string | null | undefined) {
  if (!value) return "—";
  return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value;
}
