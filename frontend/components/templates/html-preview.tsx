"use client";

type HtmlPreviewProps = {
  html: string;
  title?: string;
  className?: string;
};

/** Sandboxed preview — no script execution. */
export function HtmlPreview({ html, title = "Template preview", className }: HtmlPreviewProps) {
  return (
    <iframe
      title={title}
      srcDoc={html || "<p></p>"}
      sandbox="allow-same-origin"
      className={className ?? "h-[420px] w-full rounded-xl border border-border/70 bg-white"}
    />
  );
}
