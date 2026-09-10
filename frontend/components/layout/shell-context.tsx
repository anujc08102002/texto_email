"use client";

import * as React from "react";
import { useSyncExternalStore } from "react";

type SidebarContextValue = {
  collapsed: boolean;
  setCollapsed: (value: boolean) => void;
  toggleCollapsed: () => void;
  mobileOpen: boolean;
  setMobileOpen: (value: boolean) => void;
  commandOpen: boolean;
  setCommandOpen: (value: boolean) => void;
};

const SidebarContext = React.createContext<SidebarContextValue | null>(null);

const COLLAPSE_KEY = "texto.sidebar.collapsed";
const COLLAPSE_EVENT = "texto-sidebar-change";

function subscribeCollapse(callback: () => void) {
  window.addEventListener(COLLAPSE_EVENT, callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener(COLLAPSE_EVENT, callback);
    window.removeEventListener("storage", callback);
  };
}

function getCollapseSnapshot() {
  return window.localStorage.getItem(COLLAPSE_KEY) === "true";
}

function getServerCollapseSnapshot() {
  return false;
}

export function ShellProvider({ children }: { children: React.ReactNode }) {
  const collapsed = useSyncExternalStore(
    subscribeCollapse,
    getCollapseSnapshot,
    getServerCollapseSnapshot,
  );
  const [mobileOpen, setMobileOpen] = React.useState(false);
  const [commandOpen, setCommandOpen] = React.useState(false);

  const setCollapsed = React.useCallback((value: boolean) => {
    window.localStorage.setItem(COLLAPSE_KEY, String(value));
    window.dispatchEvent(new Event(COLLAPSE_EVENT));
  }, []);

  const toggleCollapsed = React.useCallback(() => {
    setCollapsed(!(window.localStorage.getItem(COLLAPSE_KEY) === "true"));
  }, [setCollapsed]);

  React.useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setCommandOpen((open) => !open);
      }
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "b") {
        event.preventDefault();
        setCollapsed(!(window.localStorage.getItem(COLLAPSE_KEY) === "true"));
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [setCollapsed]);

  const value = React.useMemo(
    () => ({
      collapsed,
      setCollapsed,
      toggleCollapsed,
      mobileOpen,
      setMobileOpen,
      commandOpen,
      setCommandOpen,
    }),
    [collapsed, setCollapsed, toggleCollapsed, mobileOpen, commandOpen],
  );

  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
}

export function useShell() {
  const context = React.useContext(SidebarContext);
  if (!context) {
    throw new Error("useShell must be used within ShellProvider");
  }
  return context;
}
