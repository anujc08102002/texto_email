import Link from "next/link";
import { BillingOverview } from "@/components/billing/billing-overview";
import { PageHeader } from "@/components/layout/page-header";
import { ErrorState } from "@/components/ui/error-state";
import { Button } from "@/components/ui/button";
import { getPlans } from "@/services/platform";
import type { Plan } from "@/types/api";

export const dynamic = "force-dynamic";

export default async function BillingPage() {
  let plans: Plan[] = [];
  let error: string | null = null;
  try {
    plans = await getPlans();
  } catch (cause) {
    error = cause instanceof Error ? cause.message : "Unable to load plans.";
  }

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Account"
        title="Billing"
        description="Subscription, entitlements, and usage from live APIs. Razorpay arrives in a later phase."
        actions={
          <Button asChild variant="secondary" size="sm">
            <Link href="/plans">Compare plans</Link>
          </Button>
        }
      />
      {error ? <ErrorState description={error} /> : <BillingOverview plans={plans} />}
    </div>
  );
}
