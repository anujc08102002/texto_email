import { ApiKeysManager } from "@/components/api-keys/api-keys-manager";
import { PageHeader } from "@/components/layout/page-header";

export default function ApiKeysPage() {
  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Developer"
        title="API keys"
        description="Issue keys for programmatic sending. Secrets are shown once, then masked forever."
      />
      <ApiKeysManager />
    </div>
  );
}
