"use client";

/* eslint-disable @next/next/no-img-element */
import Link from "next/link";

export default function WhatsAppFAB() {
  return (
    <Link
      href="https://wa.me/919619100943"
      target="_blank"
      rel="noopener noreferrer"
      className="fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-[#008000] shadow-[0_4px_20px_rgba(0,0,0,0.15)] border border-slate-100 transition-all duration-300 hover:scale-110 hover:shadow-[0_6px_25px_rgba(37,211,102,0.35)] focus:outline-hidden"
      aria-label="Chat on WhatsApp"
    >
      {/*
        Decorative halo that expands out of the button once every 3s. It sits
        behind the icon and never takes pointer events, so it cannot block the
        tap target. The anchor is `fixed`, which is its own containing block,
        so `inset-0` anchors to the button.
      */}
      <span
        aria-hidden="true"
        className="fab-pulse-ring pointer-events-none absolute inset-0 rounded-full bg-[#25D366]"
      />
      <span className="fab-pulse-beat relative h-full w-full rounded-full">
        <img
          src="/whatsapp-image.png"
          alt="WhatsApp Chat"
          className="h-full w-full rounded-full object-cover"
        />
      </span>
    </Link>
  );
}
