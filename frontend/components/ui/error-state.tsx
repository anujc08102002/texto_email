import type { ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

function ErrorState({
  title = "Unable to load this view",
  description,
  onRetry,
  action,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
  action?: ReactNode;
}) {
  return (
    <Alert variant="error">
      <AlertTitle>{title}</AlertTitle>
      {description ? <AlertDescription className="mt-1">{description}</AlertDescription> : null}
      {onRetry || action ? (
        <div className="mt-3">
          {onRetry ? (
            <Button type="button" size="sm" variant="secondary" onClick={onRetry}>
              Try again
            </Button>
          ) : (
            action
          )}
        </div>
      ) : null}
    </Alert>
  );
}

export { ErrorState };
