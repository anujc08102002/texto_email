import Image from "next/image";
import Link from "next/link";
import { ChannelNavigation } from "@/components/layout/channel-navigation";

export function AuthFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh bg-white">
      <header className="absolute top-0 z-10 flex h-20 w-full items-center justify-between gap-4 border-b border-slate-100 px-4 sm:px-8">
        <Link href="/" aria-label="Texto home"><Image src="/texto.png" alt="Texto" width={55} height={55} /></Link>
        <ChannelNavigation marketing />
        <Link href="/" className="hidden text-sm font-medium text-slate-600 hover:text-emerald-600 sm:block">Back to home</Link>
      </header>
      <main className="relative flex min-h-dvh flex-col items-center justify-center overflow-hidden bg-white px-4 pb-12 pt-28 sm:pb-24">
        <div aria-hidden className="pointer-events-none absolute inset-0" style={{background:"radial-gradient(55% 45% at 50% 0%, rgba(0,200,150,0.10) 0%, transparent 60%)"}} />
        <div aria-hidden className="pointer-events-none absolute left-1/2 top-1/2 size-140 -translate-x-1/2 -translate-y-1/2 rounded-full bg-emerald-500/10 blur-[120px]" />
        <div className="relative z-10 w-full max-w-md rounded-3xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-900/5 md:p-9">{children}</div>
      </main>
    </div>
  );
}
