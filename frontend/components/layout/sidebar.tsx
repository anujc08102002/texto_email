"use client";

import type { ComponentType } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { Brand } from "@/components/layout/brand";
import { WorkspaceSwitcher } from "@/components/layout/workspace-switcher";
import { useShell } from "@/components/layout/shell-context";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { IconButton } from "@/components/ui/icon-button";
import { isActiveNavItem, NAV_SECTIONS } from "@/lib/navigation";
import { cn } from "@/lib/utils";

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
          ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground shadow-xs"
          : "text-sidebar-foreground/80 hover:bg-muted/80 hover:text-foreground",
      )}
    >
      {active ? <span className="absolute inset-y-2 left-0 w-[2px] rounded-full bg-primary" aria-hidden /> : null}
      <Icon
        className={cn(
          "size-4 shrink-0",
          active ? "text-primary" : "text-muted-foreground group-hover:text-foreground",
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

  return (
    <div className="flex h-full flex-col">
      <div className={cn("flex h-14 items-center gap-1 border-b border-sidebar-border/80 px-3", collapsed ? "justify-center" : "justify-between")}>
        <Brand collapsed={collapsed} />
        {showCollapse && !collapsed ? (
          <IconButton type="button" size="icon-sm" aria-label="Collapse sidebar" onClick={() => setCollapsed(true)}>
            <PanelLeftClose />
          </IconButton>
        ) : null}
      </div>

      {showCollapse && collapsed ? (
        <div className="border-b border-sidebar-border/80 px-2 py-2">
          <Tooltip delayDuration={0}>
            <TooltipTrigger asChild>
              <button
                type="button"
                aria-label="Expand sidebar"
                onClick={() => setCollapsed(false)}
                className="flex h-9 w-full items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              >
                <PanelLeftOpen className="size-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">Expand sidebar</TooltipContent>
          </Tooltip>
        </div>
      ) : null}

      <div className="border-b border-sidebar-border/80 px-2 py-3">
        <WorkspaceSwitcher collapsed={collapsed} />
      </div>

      <ScrollArea className="flex-1">
        <nav className="space-y-5 px-2 py-4">
          {NAV_SECTIONS.map((section) => (
            <div key={section.id}>
              {!collapsed ? (
                <p className="mb-1.5 px-2.5 tech-label !tracking-[0.14em]">
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
    </div>
  );
}
