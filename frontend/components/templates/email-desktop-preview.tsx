"use client";

import { Archive, ChevronLeft, ChevronRight, Inbox, Mail, MoreHorizontal, Search, Star, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";

type Props = { html: string; subject?: string; sender?: string; title?: string; className?: string };

/** Shared email device frame. Content cannot execute scripts or navigate the app. */
export function EmailDesktopPreview({ html, subject = "Your email preview", sender = "Your business", title = "Email preview", className }: Props) {
  const document = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src https: data:; font-src https: data:; base-uri 'none'; form-action 'none'"><style>body{margin:0;padding:20px;font-family:Arial,sans-serif;color:#334155;overflow-wrap:anywhere}img{max-width:100%;height:auto}*{box-sizing:border-box}</style>${html || "<p>Your email content will appear here.</p>"}`;
  return (
    <div className={cn("email-desktop min-w-0", className)} role="group" aria-label={title}>
      <div className="email-monitor-frame flex aspect-[4/3] min-h-64 w-full min-w-0 flex-col overflow-hidden rounded-xl border-[7px] border-slate-800 bg-white shadow-xl">
        <div className="email-monitor-browser flex h-6 shrink-0 items-center gap-1.5 border-b bg-slate-100 px-3" aria-hidden="true">
          <i className="size-2 rounded-full bg-red-400" /><i className="size-2 rounded-full bg-amber-400" /><i className="size-2 rounded-full bg-emerald-400" />
          <span className="mx-auto flex min-w-0 items-center gap-1.5 rounded bg-white px-2 sm:px-8 py-0.5 text-[10px] text-slate-400"><Mail className="size-3" /> Texto Mail</span>
        </div>
        <div className="email-monitor-topbar flex h-9 shrink-0 items-center justify-between gap-2 border-b px-2 sm:px-4">
          <span className="flex items-center gap-2 text-sm font-bold text-emerald-600"><Mail className="size-5" /> Mail</span>
          <span className="flex min-w-0 w-1/2 items-center gap-2 rounded-md bg-slate-100 px-3 py-1.5 text-[10px] text-slate-400" aria-hidden="true"><Search className="size-3" /> Search mail</span>
          <span className="flex size-6 items-center justify-center rounded-full bg-emerald-100 text-[10px] font-semibold text-emerald-700" aria-hidden="true">T</span>
        </div>
        <div className="flex min-h-0 min-w-0 flex-1">
          <div className="hidden w-24 shrink-0 space-y-3 border-r bg-slate-50 px-2 py-4 text-[10px] text-slate-500 sm:block" aria-hidden="true">
            <span className="flex items-center gap-1.5 rounded bg-emerald-100 px-2 py-1.5 font-semibold text-emerald-700"><Inbox className="size-3" /> Inbox</span>
            <span className="flex items-center gap-1.5 px-2"><Star className="size-3" /> Starred</span>
            <span className="flex items-center gap-1.5 px-2"><Mail className="size-3" /> Sent</span>
            <span className="flex items-center gap-1.5 px-2"><Archive className="size-3" /> Archive</span>
          </div>
          <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-white">
            <div className="email-monitor-toolbar flex shrink-0 items-center justify-between border-b px-4 py-2 text-slate-400" aria-hidden="true"><span className="flex gap-3"><Archive className="size-3" /><Trash2 className="size-3" /><MoreHorizontal className="size-3" /></span><span className="flex gap-2"><ChevronLeft className="size-3" /><ChevronRight className="size-3" /></span></div>
            <div className="email-monitor-message-header shrink-0 border-b px-4 py-2 text-left">
              <p className="truncate text-sm font-semibold text-slate-900">{subject || "Your email preview"}</p>
              <div className="mt-2 flex items-center gap-2"><span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700"><Mail className="size-3" /></span><div className="min-w-0"><p className="truncate text-[11px] font-medium text-slate-700">{sender}</p><p className="text-[10px] text-slate-400">to your customer</p></div></div>
            </div>
            <iframe title={title} srcDoc={document} sandbox="" referrerPolicy="no-referrer" className="block min-h-0 w-full flex-1 border-0 bg-white" />
          </div>
        </div>
        <div className="flex h-5 shrink-0 items-center justify-center bg-slate-800" aria-hidden="true"><span className="size-2 rounded-full bg-slate-600" /></div>
      </div>
      <div className="mx-auto h-7 w-16 bg-gradient-to-b from-slate-400 to-slate-300" aria-hidden="true" />
      <div className="mx-auto h-2 w-36 rounded-t-xl bg-slate-300" aria-hidden="true" />
    </div>
  );
}
