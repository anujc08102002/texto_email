export function ProductPreview() {
  return (
    <div className="relative min-w-0 overflow-hidden sm:overflow-visible">
      <div className="absolute -inset-3 rounded-[2rem] bg-primary/8 blur-2xl sm:-inset-6" aria-hidden />
      <div className="panel-elevated relative overflow-hidden rounded-[1.4rem] p-0 shine-border">
        <div className="flex items-center gap-2 border-b border-border/70 bg-muted/40 px-4 py-2.5">
          <span className="size-2.5 rounded-full bg-destructive/70" />
          <span className="size-2.5 rounded-full bg-warning/80" />
          <span className="size-2.5 rounded-full bg-success/70" />
          <span className="ml-2 font-mono text-[11px] text-muted-foreground">texto.app / dashboard</span>
        </div>
        <div className="grid min-h-[16rem] grid-cols-[3.25rem_1fr] bg-card sm:min-h-[22rem] sm:grid-cols-[4.5rem_1fr]">
          <div className="bg-rail flex flex-col items-center gap-3 py-4">
            <span className="size-7 rounded-lg bg-primary" />
            <span className="size-8 rounded-lg bg-white/10" />
            <span className="size-8 rounded-lg bg-white/10" />
            <span className="size-8 rounded-lg bg-white/10" />
            <span className="mt-auto size-8 rounded-full bg-white/15" />
          </div>
          <div className="space-y-4 p-5">
            <div className="flex items-end justify-between">
              <div>
                <p className="tech-label">Workspace</p>
                <p className="font-display mt-1 text-2xl tracking-tight">Overview</p>
              </div>
              <span className="rounded-full bg-success/12 px-2.5 py-1 text-[10px] font-semibold tracking-wide text-success uppercase">
                98.4% delivered
              </span>
            </div>
            <div className="grid grid-cols-3 gap-2">
              {[
                { label: "Sent", value: "12.4k" },
                { label: "Opens", value: "41%" },
                { label: "Fails", value: "0.6%" },
              ].map((metric) => (
                <div key={metric.label} className="rounded-xl border border-border/70 bg-background/70 p-3">
                  <p className="text-[11px] text-muted-foreground">{metric.label}</p>
                  <p className="mt-1 text-lg font-semibold tracking-tight">{metric.value}</p>
                </div>
              ))}
            </div>
            <div className="space-y-2">
              {[
                { to: "ada@orbit.dev", subject: "Welcome to Orbit", status: "Delivered" },
                { to: "ops@northmail.io", subject: "Invoice ready", status: "Queued" },
                { to: "sam@lattice.app", subject: "Password reset", status: "Delivered" },
              ].map((row) => (
                <div key={row.subject} className="flex items-center justify-between rounded-xl border border-border/50 px-3 py-2.5">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{row.subject}</p>
                    <p className="truncate font-mono text-[11px] text-muted-foreground">{row.to}</p>
                  </div>
                  <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium uppercase">{row.status}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
