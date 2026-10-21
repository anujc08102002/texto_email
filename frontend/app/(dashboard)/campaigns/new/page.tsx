import { CampaignComposer } from "@/components/campaigns/campaign-composer";
import { PageHeader } from "@/components/layout/page-header";

export default function NewCampaignPage() {
	return (
		<div className="flex min-h-full flex-col gap-4">
			<PageHeader
				eyebrow="Promotions"
				title="New campaign"
				description="Save an intentional draft, choose recipients, then send or schedule it."
			/>
			<CampaignComposer />
		</div>
	);
}
