import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export function AdminPreviewBanner() {
  return (
    <Alert variant="info" className="border-info/25 bg-info/5">
      <AlertTitle>Separate platform admin console</AlertTitle>
      <AlertDescription>
        Tenant workspaces cannot access these controls. Destructive actions are confirmed here and will call{" "}
        <code className="font-mono text-[11px]">/api/v1/admin/*</code> when the backend admin API ships.
      </AlertDescription>
    </Alert>
  );
}
