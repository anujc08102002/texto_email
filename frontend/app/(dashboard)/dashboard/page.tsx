import { DashboardScreen } from "@/components/dashboard/dashboard-screen";
import { getPlatformStatus } from "@/services/platform";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  let status = null;
  try {
    status = await getPlatformStatus();
  } catch {
    status = null;
  }

  return <DashboardScreen status={status} />;
}
