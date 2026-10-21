import type { LucideIcon } from "lucide-react";
import {
  Activity,
  AlertOctagon,
  BarChart3,
  Ban,
  Building2,
  ClipboardList,
  CreditCard,
  Database,
  FileCode2,
  Globe,
  HeartPulse,
  Inbox,
  KeyRound,
  LayoutDashboard,
  Mail,
  Megaphone,
  MessageSquareWarning,
  Settings,
  Shield,
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

/**
 * Tenant navigation follows the Textonext product hierarchy, translated to
 * email concepts. Entries are deliberately limited to working routes.
 */
export const NAV_SECTIONS: NavSection[] = [
  {
    id: "main",
    label: "Main",
    items: [
      { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, keywords: ["home", "overview"] },
    ],
  },
  {
    id: "personalize",
    label: "Personalize",
    items: [
      { href: "/templates", label: "Templates", icon: FileCode2, keywords: ["html", "content"] },
      { href: "/contacts", label: "Contacts", icon: Users, keywords: ["profiles", "recipients"] },
      { href: "/segments", label: "Segments", icon: Users, keywords: ["filters", "audiences"] },
    ],
  },
  {
    id: "promotions",
    label: "Promotions",
    items: [
      { href: "/campaigns", label: "Campaigns", icon: Megaphone, keywords: ["broadcast", "draft"] },
      { href: "/emails", label: "Emails", icon: Mail, keywords: ["messages", "send", "inbox"] },
    ],
  },
  {
    id: "email",
    label: "Email",
    items: [
      { href: "/domains", label: "Domains", icon: Globe, keywords: ["dns", "spf", "dkim"] },
      { href: "/suppressions", label: "Suppressions", icon: Ban, keywords: ["bounces", "complaints"] },
    ],
  },
  {
    id: "plans",
    label: "Plans",
    items: [
      { href: "/billing", label: "Subscription & usage", icon: CreditCard, keywords: ["plans", "invoice", "usage"] },
    ],
  },
  {
    id: "integrations",
    label: "Integrations",
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
      { href: "/settings", label: "Settings", icon: Settings, keywords: ["profile", "theme"] },
    ],
  },
];

/** Separate platform admin console navigation */
export const ADMIN_NAV_SECTIONS: NavSection[] = [
  {
    id: "overview",
    label: "Main",
    items: [
      { href: "/admin", label: "Dashboard", icon: Shield, keywords: ["home", "ops", "health"] },
      { href: "/admin/traffic", label: "Traffic", icon: Activity, keywords: ["http", "requests"] },
    ],
  },
  {
    id: "delivery",
    label: "Email delivery",
    items: [
      { href: "/admin/emails", label: "Email explorer", icon: Mail, keywords: ["messages", "delivery"] },
      { href: "/admin/bounces", label: "Bounces", icon: AlertOctagon, keywords: ["dsn", "failed"] },
      { href: "/admin/complaints", label: "Complaints", icon: MessageSquareWarning, keywords: ["feedback", "abuse"] },
      { href: "/admin/suppressions", label: "Suppressions", icon: Ban, keywords: ["blocked", "recipients"] },
    ],
  },
  {
    id: "directory",
    label: "Functionality",
    items: [
      { href: "/admin/tenants", label: "Tenants", icon: Building2, keywords: ["accounts", "customers"] },
      { href: "/admin/domains", label: "Domains", icon: Globe, keywords: ["dns", "spf", "dkim", "dmarc"] },
      { href: "/admin/webhooks", label: "Webhooks", icon: Webhook, keywords: ["events", "delivery"] },
    ],
  },
  {
    id: "infrastructure",
    label: "Configuration",
    items: [
      { href: "/admin/queues", label: "Queues", icon: Inbox, keywords: ["rabbitmq", "outbox", "dlq"] },
      { href: "/admin/system", label: "System", icon: HeartPulse, keywords: ["health", "redis", "postgres", "ses"] },
      { href: "/admin/database", label: "Data explorer", icon: Database, keywords: ["read only", "datasets"] },
    ],
  },
  {
    id: "governance",
    label: "Governance & audit",
    items: [
      { href: "/admin/audit", label: "Audit log", icon: ClipboardList, keywords: ["history"] },
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
