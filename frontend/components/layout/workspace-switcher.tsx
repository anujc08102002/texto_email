"use client";

import { ChevronsUpDown } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useStoredUser } from "@/hooks/use-stored-user";
import { initials } from "@/lib/format";
import { cn } from "@/lib/utils";

export function WorkspaceSwitcher({ collapsed = false }: { collapsed?: boolean }) {
  const user = useStoredUser();
  const name = user?.organization ?? "Workspace";

  const trigger = (
    <button
      type="button"
      aria-label="Workspace selector"
      className={cn(
        "flex w-full items-center gap-2 rounded-xl border border-border/70 bg-muted/40 px-2 py-1.5 text-left outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/40",
        collapsed && "justify-center px-0",
      )}
    >
      <Avatar className="size-6">
        <AvatarFallback className="text-[10px]">{initials(name)}</AvatarFallback>
      </Avatar>
      {!collapsed ? (
        <>
          <span className="min-w-0 flex-1 truncate text-xs font-medium">{name}</span>
          <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
        </>
      ) : (
        <span className="sr-only">{name}</span>
      )}
    </button>
  );

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      <DropdownMenuContent align="start" side={collapsed ? "right" : "bottom"} className="w-56">
        <DropdownMenuLabel>Workspace</DropdownMenuLabel>
        <DropdownMenuItem disabled>{name}</DropdownMenuItem>
        <DropdownMenuItem disabled>Additional workspaces will load from the tenant API</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
