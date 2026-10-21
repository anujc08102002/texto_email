import { AdminTenantDetailScreen } from "@/components/admin/admin-tenant-detail-screen";

export default async function AdminTenantDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AdminTenantDetailScreen id={id} />;
}
