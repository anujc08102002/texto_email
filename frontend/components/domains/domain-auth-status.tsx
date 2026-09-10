import { Badge } from "@/components/ui/badge";
import type { Domain, DomainVerification } from "@/types/api";

function Check({ ok }: { ok: boolean }) {
  return <span aria-hidden>{ok ? "✓" : "•"}</span>;
}

function Row({ label, status }: { label: string; status?: string }) {
  const verified = status === "VERIFIED";
  return (
    <div className="flex items-center justify-between gap-2 text-sm">
      <span className="text-muted-foreground">
        <Check ok={verified} /> {label}
      </span>
      <Badge variant={verified ? "success" : "secondary"}>{status ?? "PENDING"}</Badge>
    </div>
  );
}

export function DomainAuthStatus({
  domain,
  verification,
}: {
  domain?: Domain | null;
  verification?: DomainVerification | null;
}) {
  return (
    <div className="grid gap-2 rounded-lg border border-border/70 bg-muted/20 p-3">
      <Row label="Ownership" status={verification?.ownershipStatus ?? domain?.ownershipStatus} />
      <Row label="SPF" status={verification?.spfStatus ?? domain?.spfStatus} />
      <Row label="DKIM" status={verification?.dkimStatus ?? domain?.dkimStatus} />
      <Row label="DMARC" status={verification?.dmarcStatus ?? domain?.dmarcStatus} />
    </div>
  );
}
