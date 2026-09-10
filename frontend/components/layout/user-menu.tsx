"use client";

import { useRouter } from "next/navigation";
import { LogOut, Settings, UserRound } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { initials } from "@/lib/format";
import { clearSession, getToken, type AuthUser } from "@/lib/session";
import { logoutAccount } from "@/services/auth";

export function UserMenu({ user }: { user: AuthUser | null }) {
  const router = useRouter();

  async function signOut() {
    try {
      if (getToken()) {
        await logoutAccount();
      }
    } catch {
      /* still clear local session */
    } finally {
      clearSession();
      router.push("/login");
    }
  }

  if (!user) {
    return null;
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="flex items-center gap-2 rounded-xl p-1 pr-2 outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/40"
          aria-label="Workspace menu"
        >
          <Avatar className="size-7">
            <AvatarFallback>{initials(user.organization || user.email)}</AvatarFallback>
          </Avatar>
          <span className="hidden max-w-[140px] truncate text-left text-sm lg:block">
            <span className="block truncate font-medium leading-tight">{user.organization}</span>
            <span className="block truncate text-xs text-muted-foreground">{user.email}</span>
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>
          <p>{user.organization}</p>
          <p className="font-normal text-muted-foreground">{user.role}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => router.push("/settings")}>
          <UserRound className="size-4" />
          Account
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => router.push("/settings")}>
          <Settings className="size-4" />
          Settings
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onSelect={() => void signOut()}>
          <LogOut className="size-4" />
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
