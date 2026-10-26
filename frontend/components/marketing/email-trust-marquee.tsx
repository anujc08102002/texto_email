"use client";

import { useEffect, useRef } from "react";
import { Activity, BarChart3, CalendarClock, CheckCheck, Code2, FileText, Globe, KeyRound, ListFilter, Mail, Send, ShieldCheck, Users, Webhook } from "lucide-react";

const items = [
  { label: "Amazon SES Delivery", icon: Mail }, { label: "Verified Sender Domains", icon: ShieldCheck },
  { label: "Reusable Templates", icon: FileText }, { label: "Email Campaigns", icon: Send },
  { label: "Contact Management", icon: Users }, { label: "API Integrations", icon: Code2 },
  { label: "Audience Segments", icon: ListFilter }, { label: "Webhooks", icon: Webhook },
  { label: "REST APIs", icon: Code2 }, { label: "Delivery Analytics", icon: BarChart3 },
  { label: "Scheduled Campaigns", icon: CalendarClock }, { label: "Personalised Emails", icon: Mail },
  { label: "Delivery History", icon: CheckCheck }, { label: "Domain Authentication", icon: Globe },
  { label: "API Keys", icon: KeyRound }, { label: "Bounce & Complaint Events", icon: Activity },
];

/** Textonext's 42px/sec seamless loop, including hover pause and drag/swipe. */
export default function EmailTrustMarquee() {
  const viewport = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const vp = viewport.current, rail = track.current;
    if (!vp || !rail) return;
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let reduced = preference.matches, position = 0, span = 0, frame = 0, previous = 0;
    let hovering = false, focused = false, stopped = false;
    let drag: { id: number; x: number; start: number } | null = null;
    const measure = () => {
      const first = rail.children[0] as HTMLElement;
      const clone = rail.children[items.length] as HTMLElement;
      span = clone.offsetLeft - first.offsetLeft;
    };
    const draw = () => {
      if (span > 0) position = ((position % span) - span) % span;
      rail.style.transform = reduced ? "none" : `translate3d(${position}px,0,0)`;
    };
    const tick = (time: number) => {
      if (!reduced && !hovering && !focused && !stopped && !drag && !document.hidden) {
        position -= 42 * Math.min(time - (previous || time), 64) / 1000;
        draw();
      }
      previous = time;
      frame = requestAnimationFrame(tick);
    };
    const enter = () => { hovering = true; };
    const leave = () => { hovering = false; };
    const focus = () => { focused = true; };
    const blur = () => { focused = false; };
    const down = (event: PointerEvent) => {
      if (event.button !== 0 || reduced) return;
      drag = { id: event.pointerId, x: event.clientX, start: position };
      vp.setPointerCapture(event.pointerId);
      vp.classList.add("is-dragging");
    };
    const move = (event: PointerEvent) => {
      if (!drag || drag.id !== event.pointerId) return;
      position = drag.start + event.clientX - drag.x;
      draw();
    };
    const release = () => { drag = null; vp.classList.remove("is-dragging"); };
    const key = (event: KeyboardEvent) => {
      if (event.key === " ") { event.preventDefault(); stopped = !stopped; }
      if (!reduced && ["ArrowLeft", "ArrowRight"].includes(event.key)) {
        event.preventDefault(); position += event.key === "ArrowLeft" ? 120 : -120; draw();
      }
    };
    const change = () => { reduced = preference.matches; vp.dataset.reduced = String(reduced); draw(); };
    const resize = new ResizeObserver(measure);
    resize.observe(rail);
    measure(); change();
    frame = requestAnimationFrame(tick);
    vp.addEventListener("pointerenter", enter); vp.addEventListener("pointerleave", leave);
    vp.addEventListener("focusin", focus); vp.addEventListener("focusout", blur);
    vp.addEventListener("pointerdown", down); vp.addEventListener("pointermove", move);
    vp.addEventListener("pointerup", release); vp.addEventListener("pointercancel", release);
    vp.addEventListener("lostpointercapture", release); vp.addEventListener("keydown", key);
    preference.addEventListener("change", change);
    return () => {
      cancelAnimationFrame(frame); resize.disconnect(); preference.removeEventListener("change", change);
      vp.removeEventListener("pointerenter", enter); vp.removeEventListener("pointerleave", leave);
      vp.removeEventListener("focusin", focus); vp.removeEventListener("focusout", blur);
      vp.removeEventListener("pointerdown", down); vp.removeEventListener("pointermove", move);
      vp.removeEventListener("pointerup", release); vp.removeEventListener("pointercancel", release);
      vp.removeEventListener("lostpointercapture", release); vp.removeEventListener("keydown", key);
    };
  }, []);
  return <section className="home-trust" aria-label="Platform capabilities">
    <div className="home-marquee" id="trust-marquee" ref={viewport} tabIndex={0} role="region" aria-label="Email capabilities. Drag to browse; focus to pause; use arrow keys to move or Space to toggle animation.">
      <div className="home-marquee-track" id="trust-track" ref={track}>
        {[0, 1].map(copy => items.map(({ label, icon: Icon }) => <span className="home-marquee-item" key={`${copy}-${label}`} aria-hidden={copy === 1 ? true : undefined}><Icon size={18} aria-hidden="true" />{label}</span>))}
      </div>
    </div>
  </section>;
}
