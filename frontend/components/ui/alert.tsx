import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from "lucide-react";
import { cn } from "@/lib/utils";

const alertVariants = cva("relative w-full rounded-lg border px-4 py-3 text-sm", {
  variants: {
    variant: {
      default: "border-border bg-card text-foreground",
      info: "border-info/20 bg-info/8 text-foreground",
      success: "border-success/20 bg-success/8 text-foreground",
      warning: "border-warning/30 bg-warning/10 text-foreground",
      error: "border-destructive/20 bg-destructive/8 text-foreground",
    },
  },
  defaultVariants: {
    variant: "default",
  },
});

const icons = {
  default: Info,
  info: Info,
  success: CheckCircle2,
  warning: TriangleAlert,
  error: AlertCircle,
};

function Alert({
  className,
  variant = "default",
  children,
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  const Icon = icons[variant ?? "default"];
  return (
    <div role="alert" className={cn(alertVariants({ variant }), "flex gap-3", className)} {...props}>
      <Icon className="mt-0.5 size-4 shrink-0 opacity-80" />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

function AlertTitle({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("font-medium", className)} {...props} />;
}

function AlertDescription({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("text-sm text-muted-foreground [&_a]:underline", className)} {...props} />;
}

export { Alert, AlertTitle, AlertDescription };
