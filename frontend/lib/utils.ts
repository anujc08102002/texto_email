import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function getEnvironmentLabel() {
  return process.env.NEXT_PUBLIC_APP_ENV ?? (process.env.NODE_ENV === "production" ? "Production" : "Local");
}
