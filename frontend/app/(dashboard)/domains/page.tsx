import { DomainsManager } from "@/components/domains/domains-manager";
import { PageHeader } from "@/components/layout/page-header";

export default function DomainsPage() {
  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Workspace"
        title="Domains"
        description="Sending-domain verification with SPF, DKIM, and DMARC DNS records."
      />
      <DomainsManager />
    </div>
  );
}
