import { SuppressionsManager } from "@/components/suppressions/suppressions-manager";
import { PageHeader } from "@/components/layout/page-header";

export default function SuppressionsPage() {
  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow="Analytics"
        title="Suppressions"
        description="Blocked recipients from bounces, complaints, unsubscribes, and manual entries."
      />
      <SuppressionsManager />
    </div>
  );
}
