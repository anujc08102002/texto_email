"use client";

import type { ComponentType } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { LogOut, Menu, Search, Shield } from "lucide-react";
import { motion } from "motion/react";
import { Brand } from "@/components/layout/brand";
import { ThemeToggle } from "@/components/layout/theme-toggle";
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
import { ADMIN_NAV_SECTIONS, getPageMeta, isActiveAdminNavItem } from "@/lib/navigation";
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
        "group relative flex h-9 items-center gap-2.5 rounded-lg px-2.5 text-[13px] transition-all duration-150",
        active
          ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
          : "text-sidebar-foreground/75 hover:bg-muted/70 hover:text-foreground",
      )}
    >
      {active ? <span className="absolute inset-y-2 left-0 w-[2px] rounded-full bg-primary" aria-hidden /> : null}
      <Icon className={cn("size-4 shrink-0", active ? "text-primary" : "text-muted-foreground group-hover:text-foreground")} />
      <span className="truncate">{label}</span>
    </Link>
  );
}

function AdminSidebar({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-14 items-center justify-between gap-2 border-b border-sidebar-border/80 px-3">
        <Brand href="/admin" />
        <span className="shrink-0 rounded-md border border-primary/30 bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-primary uppercase">
          Admin
        </span>
      </div>
      <ScrollArea className="flex-1">
        <nav className="space-y-5 px-2 py-4">
          {ADMIN_NAV_SECTIONS.map((section) => (
            <div key={section.id}>
              <p className="mb-1.5 px-2.5 text-[10px] font-semibold tracking-[0.16em] text-muted-foreground/75 uppercase">
                {section.label}
              </p>
              <div className="space-y-0.5">
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
  const pathname = usePathname();
  const router = useRouter();
  const admin = useAdminSession();
  const { setMobileOpen, setCommandOpen } = useShell();
  const page = getPageMeta(pathname);

  function signOut() {
    clearAdminSession();
    router.replace("/admin/login");
  }

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border/70 bg-[var(--header)] px-4 backdrop-blur-xl sm:px-6 xl:px-8">
      <IconButton className="lg:hidden" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
        <Menu />
      </IconButton>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold tracking-tight">{page.label}</p>
        <p className="hidden truncate text-[11px] text-muted-foreground sm:block">Platform administration</p>
      </div>
      <Button
        type="button"
        variant="secondary"
        className="hidden h-9 min-w-44 justify-between rounded-lg bg-muted/60 text-muted-foreground shadow-none md:inline-flex"
        onClick={() => setCommandOpen(true)}
      >
        <span className="flex items-center gap-2">
          <Search className="size-4" />
          Search
        </span>
        <kbd className="rounded border border-border/80 bg-card px-1.5 py-0.5 font-mono text-[10px]">⌘K</kbd>
      </Button>
      <span className="hidden items-center gap-1.5 rounded-md border border-primary/30 bg-primary/10 px-2 py-1 text-[10px] font-semibold tracking-[0.12em] text-primary uppercase sm:inline-flex">
        <Shield className="size-3" />
        Platform
      </span>
      <ThemeToggle />
      <div className="hidden text-right text-xs sm:block">
        <p className="font-medium">{admin?.name ?? "Admin"}</p>
        <p className="font-mono text-[10px] text-muted-foreground">{admin?.email}</p>
      </div>
      <IconButton aria-label="Sign out of admin" onClick={signOut}>
        <LogOut />
      </IconButton>
    </header>
  );
}

function AdminCommandMenu() {
  const router = useRouter();
  const { commandOpen, setCommandOpen } = useShell();

  return (
    <CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
      <CommandInput placeholder="Search admin pages…" />
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
  const { mobileOpen, setMobileOpen } = useShell();

  useEffect(() => {
    if (isClient && !admin) {
      router.replace("/admin/login");
    }
  }, [admin, isClient, router]);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname, setMobileOpen]);

  if (!isClient || !admin) {
    return <div className="min-h-screen bg-app" />;
  }

  return (
    <div className="min-h-screen bg-app">
      <div className="flex min-h-screen">
        <aside className="sticky top-0 z-20 hidden h-screen w-[260px] shrink-0 border-r border-sidebar-border bg-sidebar/95 backdrop-blur-xl lg:block">
          <AdminSidebar />
        </aside>
        <Drawer open={mobileOpen} onOpenChange={setMobileOpen} direction="left">
          <DrawerContent>
            <DrawerTitle className="sr-only">Admin navigation</DrawerTitle>
            <DrawerDescription className="sr-only">Platform administration</DrawerDescription>
            <AdminSidebar onNavigate={() => setMobileOpen(false)} />
          </DrawerContent>
        </Drawer>
        <div className="flex min-w-0 flex-1 flex-col">
          <AdminHeader />
          <main className="relative flex-1 px-4 py-6 sm:px-6 lg:px-8 xl:px-10">
            <motion.div
              key={pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: duration.base, ease: easeOut }}
              className="relative w-full"
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
