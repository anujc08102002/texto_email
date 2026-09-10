import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { Megaphone } from "lucide-react";

export default function CampaignsPage() {
  return (
    <div>
      <PageHeader
        eyebrow="Workspace"
        title="Campaigns"
        description="Broadcast orchestration will load from GET /api/v1/campaigns."
        actions={
          <Button type="button" disabled>
            New campaign
          </Button>
        }
      />
      <SectionPanel>
        <EmptyState
          icon={<Megaphone className="size-5" />}
          title="No campaigns yet"
          description="Campaign scheduling and audience selection wait on the growth APIs."
        />
      </SectionPanel>
    </div>
  );
}
