import { PageHeader } from "@/components/layout/page-header";
import { SegmentsWorkspace } from "@/components/segments/segments-workspace";

export default function SegmentsPage() {
	return (
		<div className="flex min-h-full flex-col gap-4">
			<PageHeader
				eyebrow="Personalize"
				title="Segments"
				description="Build reusable contact audiences with preview before saving."
			/>
			<SegmentsWorkspace />
		</div>
	);
}
