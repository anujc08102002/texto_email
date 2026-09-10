import { Badge } from "@/components/ui/badge";

const STATUS_VARIANT: Record<string, "success" | "warning" | "error" | "info" | "secondary"> = {
  SENT: "success",
  DELIVERED: "success",
  QUEUED: "info",
  PROCESSING: "info",
  SENDING: "info",
  DEFERRED: "warning",
  RETRY: "warning",
  FAILED: "error",
  BOUNCED: "error",
  SUPPRESSED: "error",
};

export function EmailStatusBadge({ status }: { status: string }) {
  return <Badge variant={STATUS_VARIANT[status] ?? "secondary"}>{status}</Badge>;
}
