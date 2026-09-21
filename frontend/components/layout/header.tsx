"use client";

import { usePathname } from "next/navigation";
import { Bell, Menu, Search } from "lucide-react";
import { useShell } from "@/components/layout/shell-context";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { UserMenu } from "@/components/layout/user-menu";
import { Button } from "@/components/ui/button";
import { IconButton } from "@/components/ui/icon-button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useStoredUser } from "@/hooks/use-stored-user";
import { getPageMeta } from "@/lib/navigation";
import { getEnvironmentLabel } from "@/lib/utils";
import { cn } from "@/lib/utils";

export function Header() {
  const pathname = usePathname();
  const { setCommandOpen, setMobileOpen } = useShell();
  const user = useStoredUser();
  const page = getPageMeta(pathname);
  const env = getEnvironmentLabel();
  const isLive = env.toLowerCase() === "production" || env.toLowerCase() === "live";

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b border-border/60 bg-[var(--header)] px-3 pr-2 backdrop-blur-2xl transition-all duration-300 sm:h-[3.75rem] sm:gap-3 sm:px-5 sm:pr-3 lg:px-8 lg:pr-4 [padding-left:max(0.75rem,env(safe-area-inset-left))]">
      <IconButton className="lg:hidden transition-all duration-200 hover:scale-110 active:scale-95" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
        <Menu />
      </IconButton>

      <div className="min-w-0 shrink">
        <p className="truncate text-[13px] text-muted-foreground">
          <span className="hidden md:inline">{user?.organization ?? "Workspace"}</span>
          <span className="hidden text-border md:inline"> / </span>
          <span className="font-medium text-foreground">{page.label}</span>
        </p>
      </div>

      <Button
        type="button"
        variant="secondary"
        className="hidden h-9 min-w-0 flex-1 justify-between rounded-full border-border/70 bg-card/70 px-3.5 text-muted-foreground shadow-none transition-all duration-200 hover:bg-card/90 hover:shadow-md md:inline-flex md:max-w-xl lg:max-w-2xl"
        onClick={() => setCommandOpen(true)}
      >
        <span className="flex items-center gap-2">
          <Search className="size-3.5 transition-transform duration-200" />
          Search workspace
        </span>
        <kbd className="rounded-md border border-border/80 bg-background/80 px-1.5 py-0.5 font-mono text-[10px] transition-all duration-200">⌘K</kbd>
      </Button>
      <IconButton className="md:hidden transition-all duration-200 hover:scale-110 active:scale-95" aria-label="Open command menu" onClick={() => setCommandOpen(true)}>
        <Search />
      </IconButton>

      <div className="ml-auto flex items-center gap-2">
        <span
          className={cn(
            "hidden items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-semibold tracking-[0.14em] uppercase transition-all duration-300 hover:scale-105 lg:inline-flex",
            isLive
              ? "border-success/30 bg-success/10 text-success"
              : "border-border/80 bg-card text-muted-foreground",
          )}
        >
          <span className={cn("size-1.5 rounded-full animate-pulse", isLive ? "bg-success" : "bg-warning")} />
          {isLive ? "Live" : env}
        </span>

        <ThemeToggle />
        <Popover>
          <PopoverTrigger asChild>
            <IconButton className="transition-all duration-200 hover:scale-110 active:scale-95" aria-label="Notifications">
              <Bell />
            </IconButton>
          </PopoverTrigger>
          <PopoverContent align="end" className="w-[min(20rem,calc(100vw-2rem))]">
            <p className="text-sm font-medium">Notifications</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Delivery events and billing alerts will appear here once the notifications API is available.
            </p>
          </PopoverContent>
        </Popover>
        <UserMenu user={user} />
      </div>
    </header>
  );
}
