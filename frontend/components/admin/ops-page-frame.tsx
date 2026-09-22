import { PageHeader } from "@/components/layout/page-header";

export function OpsPageFrame({ eyebrow = "Operations", title, description, actions, children }: { eyebrow?: string; title: string; description: string; actions?: React.ReactNode; children: React.ReactNode }) {
  return <div className="flex min-h-full flex-col gap-4 lg:gap-5"><PageHeader eyebrow={eyebrow} title={title} description={description} actions={actions} />{children}</div>;
}
