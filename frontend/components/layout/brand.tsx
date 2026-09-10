"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";

export function Brand({
  collapsed = false,
  href = "/dashboard",
  className,
}: {
  collapsed?: boolean;
  href?: string;
  className?: string;
}) {
  return (
    <Link
      href={href}
      className={cn(
        "flex items-center gap-2.5 rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-ring/40",
        className,
      )}
    >
      <span className="relative flex size-8 items-center justify-center overflow-hidden rounded-xl bg-primary text-primary-foreground shadow-sm">
        <span className="absolute inset-x-0 top-0 h-1/2 bg-white/20" aria-hidden />
        <svg viewBox="0 0 24 24" className="relative size-4" aria-hidden>
          <path
            fill="currentColor"
            d="M4 7.5A2.5 2.5 0 0 1 6.5 5h11A2.5 2.5 0 0 1 20 7.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5v-9Zm2.2.7 5.4 3.7a.8.8 0 0 0 .8 0l5.4-3.7H6.2Zm11.6 1.4-5.1 3.5a2.3 2.3 0 0 1-2.4 0L5.2 9.6v6.9c0 .44.36.8.8.8h11.8c.44 0 .8-.36.8-.8V9.6Z"
          />
        </svg>
      </span>
      {!collapsed ? (
        <span className="min-w-0">
          <span className="block text-sm font-semibold tracking-tight text-foreground">Texto</span>
          <span className="block text-[11px] text-muted-foreground">Email infrastructure</span>
        </span>
      ) : (
        <span className="sr-only">Texto</span>
      )}
    </Link>
  );
}
