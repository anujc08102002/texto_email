import { Brand } from "@/components/layout/brand";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="bg-app relative min-h-screen overflow-hidden">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute top-[-8rem] left-1/2 h-80 w-[40rem] -translate-x-1/2 rounded-full bg-primary/20 blur-3xl" />
      </div>
      <div className="relative mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-12">
        <div className="rounded-2xl border border-border/80 bg-card/80 p-8 shadow-sm backdrop-blur-xl shine-border">
          <Brand />
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </main>
  );
}
