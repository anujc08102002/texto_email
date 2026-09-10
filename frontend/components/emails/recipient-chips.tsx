"use client";

import { useState } from "react";
import { X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function RecipientChips({
  label,
  values,
  onChange,
  required = false,
  id,
}: {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  required?: boolean;
  id: string;
}) {
  const [draft, setDraft] = useState("");
  const [invalid, setInvalid] = useState(false);

  function commit(raw: string) {
    const email = raw.trim().replace(/,$/, "");
    if (!email) {
      return;
    }
    if (!EMAIL_PATTERN.test(email)) {
      setInvalid(true);
      return;
    }
    if (!values.includes(email)) {
      onChange([...values, email]);
    }
    setDraft("");
    setInvalid(false);
  }

  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
        {required ? <span className="text-destructive"> *</span> : null}
      </label>
      <div
        className={cn(
          "flex min-h-9 flex-wrap items-center gap-1.5 rounded-md border border-input bg-card px-2 py-1 shadow-xs",
          "focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/30",
          invalid && "border-destructive ring-2 ring-destructive/20",
        )}
      >
        {values.map((email) => (
          <Badge key={email} variant="secondary" className="normal-case tracking-normal">
            {email}
            <button
              type="button"
              className="ml-1 rounded-sm hover:text-foreground"
              aria-label={`Remove ${email}`}
              onClick={() => onChange(values.filter((value) => value !== email))}
            >
              <X className="size-3" />
            </button>
          </Badge>
        ))}
        <Input
          id={id}
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value);
            setInvalid(false);
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === ",") {
              event.preventDefault();
              commit(draft);
            }
            if (event.key === "Backspace" && !draft && values.length > 0) {
              onChange(values.slice(0, -1));
            }
          }}
          onBlur={() => commit(draft)}
          className="h-7 min-w-40 flex-1 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0"
          placeholder={values.length === 0 ? "name@company.com" : ""}
        />
      </div>
    </div>
  );
}
