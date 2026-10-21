import Link from "next/link";
import { Mail, MessageCircle } from "lucide-react";

export function ChannelNavigation({ marketing = false }: { marketing?: boolean }) {
  const whatsappOrigin = (process.env.NEXT_PUBLIC_WHATSAPP_BASE_URL || "https://texto.in").replace(/\/$/, "");
  return (
    <nav aria-label="Platform" className="flex items-center gap-1 rounded-lg bg-gray-100 p-1 text-xs font-medium sm:text-sm">
      <a href={`${whatsappOrigin}${marketing ? "/" : "/tenant/dashboard"}`} className="inline-flex items-center gap-1.5 rounded-md px-2 py-2 text-slate-600 transition-colors hover:bg-white hover:text-emerald-700 sm:px-3"><MessageCircle className="size-4" /><span>WhatsApp</span></a>
      <Link href={marketing ? "/" : "/dashboard"} aria-current="page" className="inline-flex items-center gap-1.5 rounded-md bg-white px-2 py-2 text-emerald-700 shadow-sm sm:px-3"><Mail className="size-4" /><span>Email</span></Link>
    </nav>
  );
}
