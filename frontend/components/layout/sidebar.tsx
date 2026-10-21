"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useShell } from "@/components/layout/shell-context";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { isActiveNavItem, NAV_SECTIONS } from "@/lib/navigation";
import { cn } from "@/lib/utils";

export function SidebarContent({ collapsed, onNavigate, showCollapse = true }: {
  collapsed: boolean; onNavigate?: () => void; showCollapse?: boolean;
}) {
  const { setCollapsed } = useShell();
  const pathname = usePathname();
  return (
    <div className="flex h-full flex-col bg-white">
      {showCollapse ? <div className="hidden h-14 shrink-0 items-center justify-end border-b px-3 lg:flex">
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setCollapsed(!collapsed)} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}>
          {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
        </Button>
      </div> : null}
      <nav className="flex-1 overflow-y-auto p-3" aria-label="Workspace">
        {NAV_SECTIONS.map((section) => <div key={section.id} className="mb-5">
          {!collapsed ? <div className="mb-2 px-3 text-[10px] font-bold uppercase tracking-widest text-emerald-600">{section.label}</div> : null}
          <div className="space-y-1">
            {section.items.map((item) => {
              const active = isActiveNavItem(pathname, item.href);
              const Icon = item.icon;
              const link = <Link key={item.href} href={item.href} onClick={onNavigate} aria-current={active ? "page" : undefined}
                className={cn("group flex h-10 items-center rounded-lg px-3 transition-colors", active ? "bg-emerald-100 text-emerald-700" : "text-gray-600 hover:bg-gray-100 hover:text-emerald-700")}>
                <Icon className="h-5 w-5 shrink-0" />
                <span className={cn("ml-3 overflow-hidden whitespace-nowrap text-sm font-medium transition-all duration-300", collapsed ? "w-0 opacity-0" : "w-auto opacity-100")}>{item.label}</span>
              </Link>;
              return collapsed ? <Tooltip key={item.href} delayDuration={0}><TooltipTrigger asChild>{link}</TooltipTrigger><TooltipContent side="right">{item.label}</TooltipContent></Tooltip> : link;
            })}
          </div>
        </div>)}
      </nav>
    </div>
  );
}
