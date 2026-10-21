"use client";

import Image from "next/image";
import Link from "next/link";
import { Menu } from "lucide-react";
import { ChannelNavigation } from "@/components/layout/channel-navigation";
import { useShell } from "@/components/layout/shell-context";
import { UserMenu } from "@/components/layout/user-menu";
import { Button } from "@/components/ui/button";
import { useStoredUser } from "@/hooks/use-stored-user";

// The header geometry, logo and profile control follow Textonext's Header.tsx.
export function Header() {
  const { setMobileOpen } = useShell();
  const user = useStoredUser();
  return (
    <header className="z-50 flex h-[70px] w-full shrink-0 items-center justify-between border-b bg-white px-6 shadow-sm">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => setMobileOpen(true)} className="h-9 w-9 cursor-pointer text-slate-600 hover:bg-slate-100 lg:hidden" aria-label="Open navigation"><Menu className="h-5 w-5" /></Button>
        <Link href="/dashboard" aria-label="Texto dashboard"><Image src="/texto.png" width={36} height={36} style={{ height: "auto" }} alt="Texto" className="shrink-0" /></Link>
      </div>
      <ChannelNavigation />
      <UserMenu user={user} />
    </header>
  );
}
