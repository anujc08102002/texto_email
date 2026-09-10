"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { toast } from "sonner";
import { IconButton } from "@/components/ui/icon-button";
import { cn } from "@/lib/utils";

export function CopyButton({
  value,
  label = "Copy",
  className,
}: {
  value: string;
  label?: string;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);

  async function onCopy() {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    toast.success("Copied");
    window.setTimeout(() => setCopied(false), 1500);
  }

  return (
    <IconButton
      type="button"
      size="icon-sm"
      variant="ghost"
      aria-label={label}
      className={cn(className)}
      onClick={onCopy}
    >
      {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
    </IconButton>
  );
}
