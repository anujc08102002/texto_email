"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { motion } from "motion/react";
import { CommandMenu } from "@/components/layout/command-menu";
import { Header } from "@/components/layout/header";
import { ShellProvider, useShell } from "@/components/layout/shell-context";
import { SidebarContent } from "@/components/layout/sidebar";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { useIsClient } from "@/hooks/use-is-client";
import { useStoredUser } from "@/hooks/use-stored-user";
import { duration, easeOut } from "@/lib/motion/variants";
import { cn } from "@/lib/utils";

function ShellFrame({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { collapsed, mobileOpen, setMobileOpen } = useShell();
  const user = useStoredUser();
  const isClient = useIsClient();
  const publicPath = pathname === "/plans";

  useEffect(() => {
    if (isClient && !user && !publicPath) {
      router.replace("/login");
    }
  }, [isClient, user, publicPath, router]);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname, setMobileOpen]);

  if (!isClient || (!user && !publicPath)) {
    return <div className="min-h-dvh bg-app" />;
  }

  return (
    <div className="flex h-dvh overflow-hidden bg-app">
      <motion.aside
        aria-label="Sidebar"
        className={cn("bg-rail z-20 hidden h-dvh shrink-0 overflow-hidden border-r border-sidebar-border lg:block")}
        animate={{ width: collapsed ? 76 : 272 }}
        transition={{ duration: duration.base, ease: easeOut }}
      >
        <SidebarContent collapsed={collapsed} />
      </motion.aside>
      <Drawer open={mobileOpen} onOpenChange={setMobileOpen} direction="left">
        <DrawerContent className="bg-rail h-dvh border-sidebar-border text-sidebar-foreground data-[vaul-drawer-direction=left]:w-[min(18rem,92vw)]">
          <DrawerTitle className="sr-only">Navigation</DrawerTitle>
          <DrawerDescription className="sr-only">Workspace navigation</DrawerDescription>
          <SidebarContent collapsed={false} showCollapse={false} onNavigate={() => setMobileOpen(false)} />
        </DrawerContent>
      </Drawer>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <Header />
        <main className="relative min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6 sm:py-5 lg:px-8 lg:py-6">
          <motion.div
            key={pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: duration.base, ease: easeOut }}
            className="relative flex min-h-full min-w-0 w-full flex-col"
          >
            {children}
          </motion.div>
        </main>
      </div>
      <CommandMenu />
    </div>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <ShellProvider>
      <ShellFrame>{children}</ShellFrame>
    </ShellProvider>
  );
}
