"use client";

import { useRouter } from "next/navigation";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { useShell } from "@/components/layout/shell-context";
import { NAV_SECTIONS } from "@/lib/navigation";
import { CreditCard, KeyRound, Mail, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";

export function CommandMenu() {
  const router = useRouter();
  const { commandOpen, setCommandOpen } = useShell();
  const { setTheme, resolvedTheme } = useTheme();

  function go(href: string) {
    setCommandOpen(false);
    router.push(href);
  }

  return (
    <CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
      <CommandInput placeholder="Search pages and actions…" />
      <CommandList>
        <CommandEmpty>No matching pages.</CommandEmpty>
        {NAV_SECTIONS.map((section) => (
          <CommandGroup key={section.id} heading={section.label}>
            {section.items.map((item) => (
              <CommandItem
                key={item.href}
                value={`${item.label} ${section.label} ${item.keywords?.join(" ") ?? ""}`}
                onSelect={() => go(item.href)}
              >
                <item.icon className="size-4 text-muted-foreground" />
                {item.label}
              </CommandItem>
            ))}
          </CommandGroup>
        ))}
        <CommandSeparator />
        <CommandGroup heading="Actions">
          <CommandItem value="compose email send" onSelect={() => go("/emails/new")}>
            <Mail className="size-4 text-muted-foreground" />
            Compose email
          </CommandItem>
          <CommandItem value="create api key" onSelect={() => go("/api-keys")}>
            <KeyRound className="size-4 text-muted-foreground" />
            Create API key
          </CommandItem>
          <CommandItem value="view plans billing" onSelect={() => go("/plans")}>
            <CreditCard className="size-4 text-muted-foreground" />
            View plans
          </CommandItem>
          <CommandItem
            value="toggle theme dark light"
            onSelect={() => {
              setTheme(resolvedTheme === "dark" ? "light" : "dark");
              setCommandOpen(false);
            }}
          >
            {resolvedTheme === "dark" ? (
              <Sun className="size-4 text-muted-foreground" />
            ) : (
              <Moon className="size-4 text-muted-foreground" />
            )}
            Toggle theme
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}
