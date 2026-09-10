"use client";

import { usePathname } from "next/navigation";
import { Bell, Menu, PanelLeft, Search } from "lucide-react";
import { useShell } from "@/components/layout/shell-context";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { UserMenu } from "@/components/layout/user-menu";
import { Button } from "@/components/ui/button";
import { IconButton } from "@/components/ui/icon-button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useStoredUser } from "@/hooks/use-stored-user";
import { getPageMeta } from "@/lib/navigation";
import { getEnvironmentLabel } from "@/lib/utils";
import { cn } from "@/lib/utils";

export function Header() {
  const pathname = usePathname();
  const { collapsed, toggleCollapsed, setCommandOpen, setMobileOpen } = useShell();
  const user = useStoredUser();
  const page = getPageMeta(pathname);
  const env = getEnvironmentLabel();
  const isLive = env.toLowerCase() === "production" || env.toLowerCase() === "live";

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border/70 bg-[var(--header)] px-4 backdrop-blur-xl sm:px-6 xl:px-8">
      <IconButton className="lg:hidden" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
        <Menu />
      </IconButton>
      <Tooltip delayDuration={0}>
        <TooltipTrigger asChild>
          <IconButton
            type="button"
            className="hidden lg:inline-flex"
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            onClick={toggleCollapsed}
          >
            <PanelLeft />
          </IconButton>
        </TooltipTrigger>
        <TooltipContent side="bottom">{collapsed ? "Expand" : "Collapse"} · ⌘B</TooltipContent>
      </Tooltip>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold tracking-tight">{page.label}</p>
        <p className="hidden truncate text-[11px] text-muted-foreground sm:block">
          {user?.organization ?? "Workspace"}
        </p>
      </div>

      <Button
        type="button"
        variant="secondary"
        className="hidden h-9 min-w-52 justify-between rounded-lg bg-muted/60 text-muted-foreground shadow-none md:inline-flex"
        onClick={() => setCommandOpen(true)}
      >
        <span className="flex items-center gap-2">
          <Search className="size-4" />
          Search
        </span>
        <kbd className="rounded border border-border/80 bg-card px-1.5 py-0.5 font-mono text-[10px]">⌘K</kbd>
      </Button>
      <IconButton className="md:hidden" aria-label="Open command menu" onClick={() => setCommandOpen(true)}>
        <Search />
      </IconButton>

      <span
        className={cn(
          "hidden items-center gap-1.5 rounded-md border px-2 py-1 text-[10px] font-semibold tracking-[0.12em] uppercase sm:inline-flex",
          isLive
            ? "border-success/30 bg-success/10 text-success"
            : "border-warning/30 bg-warning/10 text-warning-foreground",
        )}
      >
        <span className={cn("size-1.5 rounded-full", isLive ? "bg-success" : "bg-warning")} />
        {isLive ? "Live" : env}
      </span>

      <ThemeToggle />
      <Popover>
        <PopoverTrigger asChild>
          <IconButton aria-label="Notifications">
            <Bell />
          </IconButton>
        </PopoverTrigger>
        <PopoverContent align="end" className="w-80">
          <p className="text-sm font-medium">Notifications</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Delivery events and billing alerts will appear here once the notifications API is available.
          </p>
        </PopoverContent>
      </Popover>
      <UserMenu user={user} />
    </header>
  );
}
