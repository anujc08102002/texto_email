"use client";

import { useRouter } from "next/navigation";
import { ChevronDown, LogOut, Settings, UserRound } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
          className="flex cursor-pointer items-center gap-3 rounded-lg bg-gray-100 p-2 outline-none transition-colors hover:bg-emerald-50 focus-visible:ring-2 focus-visible:ring-ring/40 md:px-4 md:py-2"
          aria-label="Workspace menu"
        >
          <Avatar className="size-9">
            <AvatarFallback className="bg-emerald-500 text-white"><UserRound className="size-[18px]" /></AvatarFallback>
          </Avatar>
          <span className="hidden max-w-[180px] truncate text-left text-sm md:block">
            <span className="block truncate font-medium leading-tight">{user.organization}</span>
            <span className="block truncate text-xs text-muted-foreground">User Profile</span>
          </span>
          <ChevronDown size={18} className="hidden text-slate-500 md:block" />
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
