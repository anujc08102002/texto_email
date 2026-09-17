import Link from "next/link";
import { PageHeader } from "@/components/layout/page-header";
import { WebhookCreateForm } from "@/components/webhooks/webhook-create-form";
import { Button } from "@/components/ui/button";

export default function NewWebhookPage() {
  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Webhooks"
        title="Add endpoint"
        description="Create a signed HTTPS destination. The signing secret is shown once."
        actions={
          <Button asChild variant="secondary">
            <Link href="/webhooks">Back</Link>
          </Button>
        }
      />
      <WebhookCreateForm />
    </div>
  );
}
