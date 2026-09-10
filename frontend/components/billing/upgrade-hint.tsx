import Link from "next/link";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export function UpgradeHint({ feature }: { feature: string }) {
  return (
    <Alert variant="warning">
      <AlertTitle>{feature} is not on your plan</AlertTitle>
      <AlertDescription>
        Upgrade to unlock this feature.{" "}
        <Link href="/billing" className="font-medium text-foreground underline underline-offset-2">
          View billing
        </Link>
      </AlertDescription>
    </Alert>
  );
}
