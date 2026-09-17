"use client";

import type { ComponentType } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { PanelLeftClose, PanelLeftOpen, PenLine } from "lucide-react";
import { Brand } from "@/components/layout/brand";
import { WorkspaceSwitcher } from "@/components/layout/workspace-switcher";
import { useShell } from "@/components/layout/shell-context";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { isActiveNavItem, NAV_SECTIONS } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { useStoredUser } from "@/hooks/use-stored-user";

function NavLink({
  href,
  label,
  icon: Icon,
  collapsed,
  onNavigate,
}: {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  collapsed: boolean;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();
  const active = isActiveNavItem(pathname, href);

  const link = (
    <Link
      href={href}
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={cn(
        "group relative flex h-9 items-center gap-2.5 rounded-lg px-2.5 text-[13px] transition-colors duration-150",
        collapsed && "justify-center px-0",
        active
          ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
          : "text-sidebar-foreground/72 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground",
      )}
    >
      {active ? (
        <span className="absolute inset-y-1.5 left-0 w-[2.5px] rounded-full bg-sidebar-primary" aria-hidden />
      ) : null}
      <Icon
        className={cn(
          "size-4 shrink-0",
          active ? "text-sidebar-primary" : "text-sidebar-muted group-hover:text-sidebar-foreground",
        )}
      />
      {!collapsed ? <span className="truncate">{label}</span> : <span className="sr-only">{label}</span>}
    </Link>
  );

  if (!collapsed) return link;

  return (
    <Tooltip delayDuration={0}>
      <TooltipTrigger asChild>{link}</TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  );
}

export function SidebarContent({
  collapsed,
  onNavigate,
  showCollapse = true,
}: {
  collapsed: boolean;
  onNavigate?: () => void;
  showCollapse?: boolean;
}) {
  const { setCollapsed } = useShell();
  const pathname = usePathname();
  const user = useStoredUser();
  const composing = pathname.startsWith("/emails/new");

  const composeButton = (
    <Link
      href="/emails/new"
      onClick={onNavigate}
      className={cn(
        "flex h-9 items-center justify-center gap-2 rounded-lg bg-primary text-[13px] font-medium text-primary-foreground shadow-sm transition-opacity hover:opacity-90",
        collapsed ? "px-0" : "px-3",
      )}
    >
      <PenLine className="size-4" />
      {!collapsed ? <span>Compose</span> : <span className="sr-only">Compose</span>}
    </Link>
  );

  return (
    <div className="flex h-full flex-col text-sidebar-foreground">
      <div
        className={cn(
          "flex h-[3.75rem] items-center gap-1 border-b border-sidebar-border px-3",
          collapsed ? "justify-center" : "justify-between",
        )}
      >
        <Brand collapsed={collapsed} inverted />
        {showCollapse && !collapsed ? (
          <button
            type="button"
            aria-label="Collapse sidebar"
            onClick={() => setCollapsed(true)}
            className="flex size-8 items-center justify-center rounded-lg text-sidebar-muted transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
          >
            <PanelLeftClose className="size-4" />
          </button>
        ) : null}
      </div>

      {showCollapse && collapsed ? (
        <div className="border-b border-sidebar-border px-2 py-2">
          <Tooltip delayDuration={0}>
            <TooltipTrigger asChild>
              <button
                type="button"
                aria-label="Expand sidebar"
                onClick={() => setCollapsed(false)}
                className="flex h-9 w-full items-center justify-center rounded-lg text-sidebar-muted transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
              >
                <PanelLeftOpen className="size-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">Expand</TooltipContent>
          </Tooltip>
        </div>
      ) : null}

      <div className="border-b border-sidebar-border px-2 py-3">
        <WorkspaceSwitcher collapsed={collapsed} />
      </div>

      <div className="px-2 pt-3">
        {collapsed ? (
          <Tooltip delayDuration={0}>
            <TooltipTrigger asChild>{composeButton}</TooltipTrigger>
            <TooltipContent side="right">Compose</TooltipContent>
          </Tooltip>
        ) : (
          composeButton
        )}
        {composing ? <span className="sr-only">Currently composing</span> : null}
      </div>

      <ScrollArea className="flex-1">
        <nav className="space-y-5 px-2 py-4">
          {NAV_SECTIONS.map((section) => (
            <div key={section.id}>
              {!collapsed ? (
                <p className="mb-1.5 px-2.5 text-[10px] font-semibold tracking-[0.16em] text-sidebar-muted uppercase">
                  {section.label}
                </p>
              ) : null}
              <div className="space-y-0.5">
                {section.items.map((item) => (
                  <NavLink
                    key={item.href}
                    href={item.href}
                    label={item.label}
                    icon={item.icon}
                    collapsed={collapsed}
                    onNavigate={onNavigate}
                  />
                ))}
              </div>
            </div>
          ))}
        </nav>
      </ScrollArea>
      {!collapsed ? (
        <div className="border-t border-sidebar-border px-3 py-3">
          <p className="truncate text-xs font-medium text-sidebar-foreground">{user?.organization ?? "Workspace"}</p>
          <p className="truncate text-[11px] text-sidebar-muted">{user?.email ?? "Signed in"}</p>
        </div>
      ) : null}
    </div>
  );
}
