import { PageHeader } from "@/components/layout/page-header";
import { CampaignsWorkspace } from "@/components/campaigns/campaigns-workspace";
import { Button } from "@/components/ui/button";
import Link from "next/link";

export default function CampaignsPage() {
  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Promotions"
        title="Campaigns"
        description="Create, schedule, and track your email campaigns."
        actions={
          <Button asChild><Link href="/campaigns/new">New campaign</Link></Button>
        }
      />
      <CampaignsWorkspace />
    </div>
  );
}
