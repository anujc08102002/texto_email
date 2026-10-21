"use client";

import { EmailDesktopPreview } from "@/components/templates/email-desktop-preview";

type HtmlPreviewProps = {
  html: string;
  title?: string;
  className?: string;
  subject?: string;
};

/** Sandboxed preview — no script execution. */
export function HtmlPreview({ html, title = "Template preview", className, subject }: HtmlPreviewProps) {
  return <EmailDesktopPreview html={html} title={title} className={className} subject={subject} />;
}
