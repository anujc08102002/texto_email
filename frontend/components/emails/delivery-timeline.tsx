"use client";

import { motion } from "motion/react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

const SUCCESS_STEPS = ["QUEUED", "PROCESSING", "SENDING", "DELIVERED"] as const;
const RETRY_STEPS = ["QUEUED", "PROCESSING", "SENDING", "DEFERRED", "RETRY", "DELIVERED"] as const;

function currentIndex(status: string, steps: readonly string[]) {
  if (status === "SENT" || status === "DELIVERED") {
    return steps.length - 1;
  }
  if (status === "FAILED" || status === "BOUNCED" || status === "SUPPRESSED") {
    return -1;
  }
  const index = steps.indexOf(status);
  return index >= 0 ? index : 0;
}

export function DeliveryTimeline({ status }: { status: string }) {
  const retry = status === "DEFERRED" || status === "RETRY";
  const steps = retry ? RETRY_STEPS : SUCCESS_STEPS;
  const failed = status === "FAILED" || status === "BOUNCED" || status === "SUPPRESSED";
  const active = currentIndex(status, steps);

  return (
    <ol className="space-y-0">
      {steps.map((step, index) => {
        const complete = !failed && active >= index;
        const current = !failed && active === index;
        return (
          <li key={step} className="flex gap-3">
            <div className="flex flex-col items-center">
              <motion.span
                initial={false}
                animate={{ scale: current ? 1.05 : 1 }}
                className={cn(
                  "flex size-6 items-center justify-center rounded-full border text-[10px]",
                  complete
                    ? "border-primary bg-primary text-primary-foreground"
                    : failed && index === 0
                      ? "border-destructive bg-destructive text-destructive-foreground"
                      : "border-border bg-card text-muted-foreground",
                )}
              >
                {complete ? <Check className="size-3.5" /> : index + 1}
              </motion.span>
              {index < steps.length - 1 ? (
                <span className={cn("my-1 w-px flex-1 min-h-5", complete ? "bg-primary/40" : "bg-border")} />
              ) : null}
            </div>
            <div className="pb-4">
              <p className="text-sm font-medium">{step}</p>
              {current ? <p className="text-xs text-muted-foreground">Current status from the email API</p> : null}
            </div>
          </li>
        );
      })}
      {failed ? (
        <li className="flex gap-3">
          <span className="flex size-6 items-center justify-center rounded-full border border-destructive bg-destructive text-[10px] text-destructive-foreground">
            !
          </span>
          <div>
            <p className="text-sm font-medium">{status}</p>
            <p className="text-xs text-muted-foreground">Delivery events and SMTP transcripts will appear when the events API is available.</p>
          </div>
        </li>
      ) : null}
    </ol>
  );
}
