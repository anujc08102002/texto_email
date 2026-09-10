"use client";

import { useCallback, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/lib/api";
import { getBillingConfig, startCheckout } from "@/services/billing";

type RazorpayCheckoutHandler = {
  open: () => void;
};

type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayCheckoutHandler;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

async function loadRazorpayScript(): Promise<boolean> {
  if (typeof window === "undefined") return false;
  if (window.Razorpay) return true;
  return new Promise((resolve) => {
    const existing = document.querySelector<HTMLScriptElement>('script[data-texto-razorpay="1"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(Boolean(window.Razorpay)));
      existing.addEventListener("error", () => resolve(false));
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.dataset.textoRazorpay = "1";
    script.onload = () => resolve(Boolean(window.Razorpay));
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

export function PlanCheckoutButton({
  planCode,
  currentPlanCode,
  disabled,
  onCompleted,
}: {
  planCode: string;
  currentPlanCode?: string | null;
  disabled?: boolean;
  onCompleted?: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const isCurrent = planCode === currentPlanCode;
  const isFree = planCode === "FREE";
  const isEnterprise = planCode === "ENTERPRISE";

  const onCheckout = useCallback(async () => {
    setBusy(true);
    try {
      const config = await getBillingConfig();
      if (!config.configured || !config.keyId) {
        toast.error("Billing is not configured. Set Razorpay TEST keys and activate provider plan mappings.");
        return;
      }

      const loaded = await loadRazorpayScript();
      if (!loaded || !window.Razorpay) {
        toast.error("Unable to load Razorpay Checkout.");
        return;
      }

      const session = await startCheckout(planCode);
      const razorpay = new window.Razorpay({
        key: session.keyId || config.keyId,
        subscription_id: session.providerSubscriptionId,
        name: "Texto",
        description: `Subscribe to ${planCode}`,
        theme: { color: "#0f766e" },
        handler: () => {
          toast.success("Payment submitted. Entitlements update after webhook confirmation.");
          onCompleted?.();
        },
        modal: {
          ondismiss: () => {
            toast.message("Checkout closed");
          },
        },
      });
      razorpay.open();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Checkout failed");
    } finally {
      setBusy(false);
    }
  }, [onCompleted, planCode]);

  if (isCurrent) {
    return (
      <Button variant="secondary" size="sm" disabled>
        Current plan
      </Button>
    );
  }

  if (isFree) {
    return (
      <Button variant="secondary" size="sm" disabled>
        Included
      </Button>
    );
  }

  if (isEnterprise) {
    return (
      <Button variant="secondary" size="sm" disabled>
        Contact sales
      </Button>
    );
  }

  return (
    <Button size="sm" disabled={disabled || busy} onClick={onCheckout}>
      {busy ? "Starting…" : "Upgrade"}
    </Button>
  );
}
