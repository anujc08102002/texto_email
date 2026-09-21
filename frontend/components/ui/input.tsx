import * as React from "react";
import { cn } from "@/lib/utils";

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-10 w-full min-w-0 rounded-xl border border-input bg-card/80 px-3 py-1 text-sm shadow-xs transition-[border-color,box-shadow,transform,background-color] duration-200 ease-out outline-none placeholder:text-muted-foreground backdrop-blur-sm",
        "focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30 focus-visible:-translate-y-0.5 focus-visible:shadow-sm focus-visible:scale-[1.01]",
        "hover:border-primary/30 hover:bg-card/90 hover:scale-[1.01]",
        "disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-input disabled:hover:bg-card/80 disabled:hover:scale-100 disabled:focus-visible:translate-y-0 disabled:focus-visible:scale-100",
        "aria-invalid:border-destructive aria-invalid:ring-2 aria-invalid:ring-destructive/20",
        "file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium",
        className,
      )}
      {...props}
    />
  );
}

export { Input };
