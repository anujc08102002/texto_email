import { TemplateDetail } from "@/components/templates/template-detail";

export default async function TemplateDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <TemplateDetail templateId={id} />;
}
