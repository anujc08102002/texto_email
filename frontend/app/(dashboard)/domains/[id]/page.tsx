"use client";

import { useParams } from "next/navigation";
import { DomainDetail } from "@/components/domains/domain-detail";

export default function DomainDetailPage() {
  const params = useParams<{ id: string }>();
  return <DomainDetail domainId={String(params.id)} />;
}
