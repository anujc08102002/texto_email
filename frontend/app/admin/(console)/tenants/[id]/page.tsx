import Link from "next/link";
import { AdminTenantDetailScreen } from "@/components/admin/admin-tenant-detail-screen";
import { ErrorState } from "@/components/ui/error-state";
import { Button } from "@/components/ui/button";
import { getAdminTenant } from "@/lib/presentation/admin";

export default async function AdminTenantDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const tenant = getAdminTenant(id);

  if (!tenant) {
    return (
      <div className="space-y-4">
        <ErrorState description="Tenant not found in the admin preview dataset." />
        <Button asChild variant="secondary">
          <Link href="/admin/tenants">Back to tenants</Link>
        </Button>
      </div>
    );
  }

  return <AdminTenantDetailScreen tenant={tenant} />;
}
