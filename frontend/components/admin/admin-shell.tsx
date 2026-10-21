"use client";

import type { ComponentType } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, LogOut, Menu } from "lucide-react";
import { motion } from "motion/react";
import { Brand } from "@/components/layout/brand";
import { ChannelNavigation } from "@/components/layout/channel-navigation";
import { ShellProvider, useShell } from "@/components/layout/shell-context";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Button } from "@/components/ui/button";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { IconButton } from "@/components/ui/icon-button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useAdminSession } from "@/hooks/use-admin-session";
import { useIsClient } from "@/hooks/use-is-client";
import { clearAdminSession } from "@/lib/admin-session";
import { adminGet } from "@/services/admin-ops";
import { ADMIN_NAV_SECTIONS, isActiveAdminNavItem } from "@/lib/navigation";
import { duration, easeOut } from "@/lib/motion/variants";
import { cn } from "@/lib/utils";

function AdminNavLink({
  href,
  label,
  icon: Icon,
  onNavigate,
}: {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();
  const active = isActiveAdminNavItem(pathname, href);

  return (
    <Link
      href={href}
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={cn(
        "group relative flex h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors",
        active
          ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
          : "text-sidebar-foreground/72 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground",
      )}
    >
      {active ? <span className="absolute inset-y-1.5 left-0 w-[2.5px] rounded-full bg-sidebar-primary" aria-hidden /> : null}
      <Icon className={cn("size-5 shrink-0", active ? "text-sidebar-primary" : "text-sidebar-muted group-hover:text-sidebar-foreground")} />
      <span className="admin-nav-label truncate">{label}</span>
    </Link>
  );
}

function AdminSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { collapsed, setCollapsed } = useShell();
  return (
    <div className="admin-navigation flex h-full flex-col bg-white text-sidebar-foreground">
      <div className="hidden h-14 shrink-0 items-center justify-end border-b px-3 lg:flex"><Button variant="ghost" size="icon" className="h-8 w-8" aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} onClick={() => setCollapsed(!collapsed)}>{collapsed ? <ChevronRight className="size-4" /> : <ChevronLeft className="size-4" />}</Button></div>
      <ScrollArea className="flex-1">
        <nav className="space-y-5 p-3">
          {ADMIN_NAV_SECTIONS.map((section) => (
            <div key={section.id}>
              <p className="mb-2 px-3 text-[10px] font-bold tracking-widest text-emerald-600 uppercase">
                {section.label}
              </p>
              <div className="space-y-1">
                {section.items.map((item) => (
                  <AdminNavLink
                    key={item.href}
                    href={item.href}
                    label={item.label}
                    icon={item.icon}
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

function AdminHeader() {
  const router = useRouter();
  const admin = useAdminSession();
  const { setMobileOpen } = useShell();

  function signOut() {
    clearAdminSession();
    router.replace("/admin/login");
  }

  return (
    <header className="z-50 flex h-[70px] w-full shrink-0 items-center justify-between border-b bg-white px-6 shadow-sm">
      <div className="flex items-center gap-3"><IconButton className="lg:hidden" aria-label="Open navigation" onClick={() => setMobileOpen(true)}><Menu /></IconButton><Brand href="/admin" collapsed /></div>
      <ChannelNavigation />
      <div className="flex items-center gap-3 rounded-lg bg-gray-100 p-2 md:px-4"><span className="hidden text-sm font-medium md:block">{admin?.name ?? "Admin"}<span className="block text-xs font-normal text-slate-500">Admin Profile</span></span><IconButton aria-label="Sign out of admin" onClick={signOut}><LogOut /></IconButton></div>
    </header>
  );
}

function AdminCommandMenu() {
  const router = useRouter();
  const { commandOpen, setCommandOpen } = useShell();

  return (
    <CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
      <CommandInput placeholder="Search admin pagesÃ¢â‚¬Â¦" />
      <CommandList>
        <CommandEmpty>No matching pages.</CommandEmpty>
        {ADMIN_NAV_SECTIONS.map((section) => (
          <CommandGroup key={section.id} heading={section.label}>
            {section.items.map((item) => (
              <CommandItem
                key={item.href}
                value={`${item.label} ${item.keywords?.join(" ") ?? ""}`}
                onSelect={() => {
                  setCommandOpen(false);
                  router.push(item.href);
                }}
              >
                <item.icon className="size-4 text-muted-foreground" />
                {item.label}
              </CommandItem>
            ))}
          </CommandGroup>
        ))}
      </CommandList>
    </CommandDialog>
  );
}

function AdminFrame({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const admin = useAdminSession();
  const isClient = useIsClient();
  const [verified, setVerified] = useState(false);
  const { collapsed, mobileOpen, setMobileOpen } = useShell();

  useEffect(() => {
    if (isClient && !admin) {
      router.replace("/admin/login");
    }
  }, [admin, isClient, router]);

  useEffect(() => {
    if (!isClient || !admin) return;
    let active = true;
    adminGet("/api/v1/admin/auth/me").then(() => {
      if (active) setVerified(true);
    }).catch(() => {
      if (!active) return;
      clearAdminSession();
      router.replace("/admin/login");
    });
    return () => { active = false; };
  }, [admin, isClient, router]);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname, setMobileOpen]);

  if (!isClient || !admin || !verified) {
    return <div className="min-h-dvh bg-app" />;
  }

  return (
    <div className="texto-workspace flex h-dvh flex-col overflow-hidden bg-gray-100">
      <AdminHeader />
      <div className="flex min-h-0 flex-1">
      <aside data-collapsed={collapsed} className={cn("bg-rail z-20 hidden h-full shrink-0 border-r border-sidebar-border transition-all lg:block", collapsed ? "w-[72px]" : "w-[248px] min-[1440px]:w-[288px]")}>
        <AdminSidebar />
      </aside>
      <Drawer open={mobileOpen} onOpenChange={setMobileOpen} direction="left">
        <DrawerContent className="bg-rail h-dvh border-sidebar-border text-sidebar-foreground data-[vaul-drawer-direction=left]:w-[min(18rem,92vw)]">
          <DrawerTitle className="sr-only">Admin navigation</DrawerTitle>
          <DrawerDescription className="sr-only">Platform administration</DrawerDescription>
          <AdminSidebar onNavigate={() => setMobileOpen(false)} />
        </DrawerContent>
      </Drawer>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <main className="relative min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6 sm:py-5 lg:px-8 lg:py-6">
          <motion.div
            key={pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: duration.base, ease: easeOut }}
            className="workspace-content relative flex min-h-full min-w-0 w-full flex-col"
          >
            {children}
          </motion.div>
        </main>
      </div>
      </div>
      <AdminCommandMenu />
    </div>
  );
}

export function AdminShell({ children }: { children: React.ReactNode }) {
  return (
    <ShellProvider>
      <AdminFrame>{children}</AdminFrame>
    </ShellProvider>
  );
}
