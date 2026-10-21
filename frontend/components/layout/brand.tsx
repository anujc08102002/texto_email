import Image from "next/image";
import Link from "next/link";
import { cn } from "@/lib/utils";

export function Brand({ collapsed = false, href = "/dashboard", className }: { collapsed?: boolean; href?: string; className?: string; inverted?: boolean }) {
  return <Link href={href} aria-label="Texto" className={cn("inline-flex shrink-0 items-center gap-2", className)}><Image src="/texto.png" alt="Texto" width={collapsed ? 36 : 44} height={collapsed ? 36 : 44} /></Link>;
}
