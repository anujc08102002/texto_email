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
    return <div className="min-h-screen bg-app" />;
  }

  return (
    <div className="min-h-screen bg-app">
      <div className="flex min-h-screen">
        <motion.aside
          aria-label="Sidebar"
          className={cn(
            "sticky top-0 z-20 hidden h-screen shrink-0 overflow-hidden border-r border-sidebar-border bg-sidebar/95 backdrop-blur-xl lg:block",
          )}
          animate={{ width: collapsed ? 76 : 260 }}
          transition={{ duration: duration.base, ease: easeOut }}
        >
          <SidebarContent collapsed={collapsed} />
        </motion.aside>
        <Drawer open={mobileOpen} onOpenChange={setMobileOpen} direction="left">
          <DrawerContent>
            <DrawerTitle className="sr-only">Navigation</DrawerTitle>
            <DrawerDescription className="sr-only">Workspace navigation</DrawerDescription>
            <SidebarContent collapsed={false} showCollapse={false} onNavigate={() => setMobileOpen(false)} />
          </DrawerContent>
        </Drawer>
        <div className="flex min-w-0 flex-1 flex-col">
          <Header />
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
