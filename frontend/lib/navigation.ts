import type { LucideIcon } from "lucide-react";
import {
  BarChart3,
  Ban,
  Building2,
  ClipboardList,
  CreditCard,
  FileCode2,
  Flag,
  Globe,
  KeyRound,
  LayoutDashboard,
  Mail,
  Megaphone,
  MessageSquareWarning,
  Scale,
  Settings,
  Shield,
  UserPlus,
  Users,
  Webhook,
} from "lucide-react";

export type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  keywords?: string[];
};

export type NavSection = {
  id: string;
  label: string;
  items: NavItem[];
};

/** Tenant workspace navigation — IA aligned to Workspace / Developer / Analytics / Account */
export const NAV_SECTIONS: NavSection[] = [
  {
    id: "workspace",
    label: "Workspace",
    items: [
      { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, keywords: ["home", "overview"] },
      { href: "/emails", label: "Emails", icon: Mail, keywords: ["messages", "send", "inbox"] },
      { href: "/templates", label: "Templates", icon: FileCode2, keywords: ["html"] },
      { href: "/domains", label: "Domains", icon: Globe, keywords: ["dns", "spf", "dkim"] },
      { href: "/campaigns", label: "Campaigns", icon: Megaphone, keywords: ["broadcast"] },
    ],
  },
  {
    id: "developer",
    label: "Developer",
    items: [
      { href: "/api-keys", label: "API Keys", icon: KeyRound, keywords: ["token", "secret"] },
      { href: "/webhooks", label: "Webhooks", icon: Webhook, keywords: ["events", "endpoint"] },
    ],
  },
  {
    id: "analytics",
    label: "Analytics",
    items: [
      { href: "/analytics", label: "Analytics", icon: BarChart3, keywords: ["reports", "metrics"] },
      { href: "/suppressions", label: "Suppressions", icon: Ban, keywords: ["bounces", "complaints"] },
    ],
  },
  {
    id: "account",
    label: "Account",
    items: [
      { href: "/billing", label: "Billing", icon: CreditCard, keywords: ["plans", "invoice", "usage"] },
      { href: "/settings", label: "Settings", icon: Settings, keywords: ["profile", "theme"] },
    ],
  },
];

/** Separate platform admin console navigation */
export const ADMIN_NAV_SECTIONS: NavSection[] = [
  {
    id: "overview",
    label: "Overview",
    items: [
      { href: "/admin", label: "Control plane", icon: Shield, keywords: ["home", "ops"] },
      { href: "/admin/insights", label: "Platform insights", icon: BarChart3, keywords: ["analytics", "fleet"] },
    ],
  },
  {
    id: "tenants",
    label: "Tenants",
    items: [
      { href: "/admin/tenants", label: "All tenants", icon: Building2, keywords: ["accounts", "customers"] },
      { href: "/admin/accounts/create", label: "Create account", icon: UserPlus, keywords: ["register", "provision"] },
    ],
  },
  {
    id: "operations",
    label: "Operations",
    items: [
      { href: "/admin/billing", label: "Billing ops", icon: CreditCard, keywords: ["dunning", "invoices"] },
      { href: "/admin/messages", label: "Messages", icon: MessageSquareWarning, keywords: ["notify", "email"] },
      { href: "/admin/compliance", label: "Compliance", icon: Scale, keywords: ["abuse", "spam"] },
    ],
  },
  {
    id: "governance",
    label: "Governance",
    items: [
      { href: "/admin/audit", label: "Audit log", icon: ClipboardList, keywords: ["history"] },
      { href: "/admin/platform", label: "Platform controls", icon: Flag, keywords: ["flags", "maintenance"] },
    ],
  },
];

export const ALL_NAV_ITEMS = NAV_SECTIONS.flatMap((section) => section.items);
export const ALL_ADMIN_NAV_ITEMS = ADMIN_NAV_SECTIONS.flatMap((section) => section.items);

function isNavActive(pathname: string, href: string, exactOnly: string[] = ["/dashboard", "/admin"]) {
  if (pathname === href) return true;
  if (exactOnly.includes(href)) return false;
  return pathname.startsWith(`${href}/`);
}

export function isActiveNavItem(pathname: string, href: string) {
  return isNavActive(pathname, href);
}

export function isActiveAdminNavItem(pathname: string, href: string) {
  return isNavActive(pathname, href, ["/admin"]);
}

export function getPageMeta(pathname: string) {
  const adminExact = ALL_ADMIN_NAV_ITEMS.find((item) => item.href === pathname);
  if (adminExact) return adminExact;
  if (pathname.startsWith("/admin/tenants/")) {
    return { href: pathname, label: "Tenant control", icon: Users };
  }
  if (pathname.startsWith("/admin")) {
    return { href: pathname, label: "Admin", icon: Shield };
  }

  const exact = ALL_NAV_ITEMS.find((item) => item.href === pathname);
  if (exact) return exact;
  if (pathname.startsWith("/emails/new")) return { href: "/emails/new", label: "Compose", icon: Mail };
  if (pathname.startsWith("/emails/")) return { href: pathname, label: "Message", icon: Mail };
  if (pathname.startsWith("/templates/new")) return { href: "/templates/new", label: "New template", icon: FileCode2 };
  if (pathname.startsWith("/templates/")) return { href: pathname, label: "Template", icon: FileCode2 };
  if (pathname.startsWith("/domains/")) return { href: pathname, label: "Domain", icon: Globe };
  if (pathname.startsWith("/webhooks/new")) return { href: "/webhooks/new", label: "New webhook", icon: Webhook };
  if (pathname.startsWith("/webhooks/")) return { href: pathname, label: "Webhook", icon: Webhook };
  if (pathname.startsWith("/plans")) return { href: "/plans", label: "Plans", icon: CreditCard };
  return { href: pathname, label: "Texto", icon: LayoutDashboard };
}
