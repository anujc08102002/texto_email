import { apiGet } from "@/lib/api";
import type { Analytics } from "@/types/api";

export function getAnalytics() {
  return apiGet<Analytics>("/api/v1/analytics");
}
