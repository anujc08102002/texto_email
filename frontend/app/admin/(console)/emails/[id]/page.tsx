import { AdminEmailDetailScreen } from "@/components/admin/admin-email-detail-screen";
export default async function Page({ params }: { params: Promise<{ id: string }> }) { const { id } = await params; return <AdminEmailDetailScreen id={id} />; }
