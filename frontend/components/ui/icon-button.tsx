import * as React from "react";
import { Button, type ButtonProps } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function IconButton({
  className,
  size = "icon",
  variant = "ghost",
  ...props
}: ButtonProps) {
  return <Button variant={variant} size={size} className={cn(className)} {...props} />;
}
