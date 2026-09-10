import type { AdminTenantStatus } from "@/types/admin";
import { Badge } from "@/components/ui/badge";

const STATUS_VARIANT: Record<
  AdminTenantStatus,
  "success" | "warning" | "error" | "secondary" | "info" | "outline"
> = {
  active: "success",
  trial: "info",
  past_due: "warning",
  suspended: "error",
  sending_frozen: "warning",
  closed: "secondary",
};

export function TenantStatusBadge({ status }: { status: AdminTenantStatus }) {
  return (
    <Badge variant={STATUS_VARIANT[status]} className="whitespace-nowrap">
      {status.replaceAll("_", " ")}
    </Badge>
  );
}
