"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChannelNavigation } from "@/components/layout/channel-navigation";
import { corporateUrl } from "./marketing-links";

/**
 * Marketing top nav — ported from Navbar.ascx.
 * - `#site-nav` + the Tailwind utility classes are repainted for the light
 *   homepage by the `.home-light #site-nav …` rules in texto-home.css.
 * - Scroll-blur (`home-nav-scrolled` past 40px) ports `runNav()` from
 *   texto-home.js.
 * - Desktop dropdowns keep the CSS `group-hover` behavior from the legacy.
 * - The mobile panel is an accessible in-place toggle (replaces the legacy
 *   `data-mobile-toggle` script). It stays inside `#site-nav` so the
 *   light-theme overrides apply — a portaled Sheet would fall outside that
 *   scope.
 *
 * The 55×55 brand mark uses /public/texto.svg for both logo slots (the
 * marketing site is always light, so `.home-logo-light` is the one shown).
 */

const PRODUCT = [
  { href: "/#email-api", label: "Email Sending API" },
  { href: "/#email-templates", label: "Email Templates" },
  { href: "/#email-campaigns", label: "Email Campaigns" },
  { href: "/#email-integrations", label: "Integrations" },
  { href: "/#email-analytics", label: "Analytics" },
];

const SOLUTIONS = [
  { href: corporateUrl("/solutions/logistics"), label: "Logistics" },
  { href: corporateUrl("/solutions/ecommerce"), label: "E-commerce & D2C" },
  { href: corporateUrl("/solutions/bfsi"), label: "BFSI" },
  { href: corporateUrl("/solutions/healthcare"), label: "Healthcare" },
  { href: corporateUrl("/solutions/travel"), label: "Travel & Hospitality" },
  { href: corporateUrl("/solutions/edtech"), label: "EdTech" },
];

const CAREERS = [
  { href: corporateUrl("/careers"), label: "Careers" },
  { href: corporateUrl("/partners"), label: "Partners" },
];

const Chevron = () => (
  <span className="inline-block">
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" className="w-3 h-3">
      <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  </span>
);

export default function MarketingNavbar() {
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  // Scroll-blur toggle (ports runNav()).
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 40);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // Close the mobile menu on Escape.
  useEffect(() => {
    if (!mobileOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") { setMobileOpen(false); setOpenMenu(null); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  return (
    <nav
      id="site-nav" aria-label="Main navigation"
      className={`fixed top-0 w-full z-50 transition-all duration-300 border-b border-transparent bg-background/95 backdrop-blur-md border-white/10${
        scrolled ? " home-nav-scrolled" : ""
      }`}
    >
      <div className="marketing-nav-container container mx-auto px-4 md:px-6">
        <div className="flex items-center justify-between h-20">
          <Link href="/" className="flex items-center gap-2 hover:opacity-90 transition-opacity">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="home-logo-dark" src="/texto.svg" alt="Texto" style={{ width: 75, height: 75 }} />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="home-logo-light" src="/texto.png" alt="Texto" style={{ width: 55, height: 55 }} />
          </Link>

          {/* Desktop nav */}
          <div className="hidden lg:flex items-center gap-6">
            <Link href="/" className="text-sm font-medium transition-colors text-gray-300 hover:text-white">
              Home
            </Link>
            <Link href={corporateUrl("/about")} className="text-sm font-medium transition-colors text-gray-300 hover:text-white">
              About Us
            </Link>

            <div className="relative group marketing-menu" onMouseEnter={() => setOpenMenu("Product")} onMouseLeave={() => setOpenMenu(null)} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpenMenu(null); }} onKeyDown={(event) => { if (event.key === "Escape") setOpenMenu(null); }}>
              <button type="button" aria-expanded={openMenu === "Product"} aria-controls="marketing-product-menu" onClick={() => setOpenMenu(openMenu === "Product" ? null : "Product")} className="flex items-center gap-1 text-sm font-medium transition-colors py-2 text-gray-300 group-hover:text-white">
                Product <Chevron />
              </button>
              <div id="marketing-product-menu" className={`absolute top-full left-0 w-56 pt-2 transition-all duration-200 ${openMenu === "Product" ? "opacity-100 visible" : "opacity-0 invisible"}`}>
                <div className="bg-[#0B0F13] border border-white/10 rounded-xl p-2 shadow-xl flex flex-col gap-1">
                  <div className="px-2 pb-2"><ChannelNavigation marketing /></div>
                  {PRODUCT.map((i) => (
                    <Link key={i.label} href={i.href} onClick={() => setOpenMenu(null)} className="block px-4 py-2 text-sm rounded-lg transition-colors text-gray-400 hover:text-white hover:bg-white/5">
                      {i.label}
                    </Link>
                  ))}
                </div>
              </div>
            </div>

            <div className="relative group marketing-menu" onMouseEnter={() => setOpenMenu("Solutions")} onMouseLeave={() => setOpenMenu(null)} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpenMenu(null); }} onKeyDown={(event) => { if (event.key === "Escape") setOpenMenu(null); }}>
              <button type="button" aria-expanded={openMenu === "Solutions"} aria-controls="marketing-solutions-menu" onClick={() => setOpenMenu(openMenu === "Solutions" ? null : "Solutions")} className="flex items-center gap-1 text-sm font-medium transition-colors py-2 text-gray-300 group-hover:text-white">
                Solutions <Chevron />
              </button>
              <div id="marketing-solutions-menu" className={`absolute top-full left-0 w-56 pt-2 transition-all duration-200 ${openMenu === "Solutions" ? "opacity-100 visible" : "opacity-0 invisible"}`}>
                <div className="bg-[#0B0F13] border border-white/10 rounded-xl p-2 shadow-xl flex flex-col gap-1">
                  {SOLUTIONS.map((i) => (
                    <Link key={i.label} href={i.href} onClick={() => setOpenMenu(null)} className="block px-4 py-2 text-sm rounded-lg transition-colors text-gray-400 hover:text-white hover:bg-white/5">
                      {i.label}
                    </Link>
                  ))}
                </div>
              </div>
            </div>

            <Link href="/plans" className="text-sm font-medium transition-colors text-gray-300 hover:text-white">
              Subscriptions
            </Link>

            <div className="relative group marketing-menu" onMouseEnter={() => setOpenMenu("Careers")} onMouseLeave={() => setOpenMenu(null)} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpenMenu(null); }} onKeyDown={(event) => { if (event.key === "Escape") setOpenMenu(null); }}>
              <button type="button" aria-expanded={openMenu === "Careers"} aria-controls="marketing-careers-menu" onClick={() => setOpenMenu(openMenu === "Careers" ? null : "Careers")} className="flex items-center gap-1 text-sm font-medium transition-colors py-2 text-gray-300 group-hover:text-white">
                Careers <Chevron />
              </button>
              <div id="marketing-careers-menu" className={`absolute top-full left-0 w-56 pt-2 transition-all duration-200 ${openMenu === "Careers" ? "opacity-100 visible" : "opacity-0 invisible"}`}>
                <div className="bg-[#0B0F13] border border-white/10 rounded-xl p-2 shadow-xl flex flex-col gap-1">
                  {CAREERS.map((i) => (
                    <Link key={i.label} href={i.href} onClick={() => setOpenMenu(null)} className="block px-4 py-2 text-sm rounded-lg transition-colors text-gray-400 hover:text-white hover:bg-white/5">
                      {i.label}
                    </Link>
                  ))}
                </div>
              </div>
            </div>

            <Link href={corporateUrl("/contact")} className="text-sm font-medium transition-colors text-gray-300 hover:text-white">
              Contact Us
            </Link>
          </div>

          <div className="flex items-center gap-4">
            <div className="hidden md:block">
              <Link href="/login" className="inline-flex items-center justify-center rounded-lg font-medium transition-colors hover:bg-white/5 text-gray-300 hover:text-white mr-2 h-11 px-6 text-base">
                Log In
              </Link>
              <Link href={corporateUrl("/contact")} className="inline-flex items-center justify-center rounded-lg font-medium transition-colors bg-primary text-white hover:text-white hover:bg-primary-hover h-11 px-6 text-base">
                Book a Demo
              </Link>
            </div>
            <button
              type="button"
              className="lg:hidden p-2 text-gray-300 hover:text-white"
              aria-label="Toggle navigation menu"
              aria-expanded={mobileOpen}
              aria-controls="site-nav-mobile"
              onClick={() => setMobileOpen((v) => !v)}
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M3 6h18M3 12h18M3 18h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      {/* Mobile panel */}
      <div
        id="site-nav-mobile"
        className={`lg:hidden bg-background border-b border-white/10 max-h-[calc(100vh-80px)] overflow-y-auto${
          mobileOpen ? "" : " hidden"
        }`}
      >
        <div className="container mx-auto px-4 py-4 flex flex-col gap-6">
          <ChannelNavigation marketing />
          <Link href="/" className="font-bold transition-colors text-white" onClick={() => setMobileOpen(false)}>
            Home
          </Link>
          <Link href={corporateUrl("/about")} className="font-bold transition-colors text-white" onClick={() => setMobileOpen(false)}>
            About Us
          </Link>
          <div>
            <div className="font-bold mb-2 text-white">Product</div>
            <div className="flex flex-col gap-2 pl-4 border-l border-white/10">
              {PRODUCT.map((i) => (
                <Link key={i.label} href={i.href} className="transition-colors text-gray-400 hover:text-white" onClick={() => setMobileOpen(false)}>
                  {i.label}
                </Link>
              ))}
            </div>
          </div>
          <div>
            <div className="font-bold mb-2 text-white">Solutions</div>
            <div className="flex flex-col gap-2 pl-4 border-l border-white/10">
              {SOLUTIONS.map((i) => (
                <Link key={i.label} href={i.href} className="transition-colors text-gray-400 hover:text-white" onClick={() => setMobileOpen(false)}>
                  {i.label}
                </Link>
              ))}
            </div>
          </div>
          <Link href="/plans" className="font-bold transition-colors text-white" onClick={() => setMobileOpen(false)}>
            Subscriptions
          </Link>
          <Link href={corporateUrl("/careers")} className="font-bold transition-colors text-white" onClick={() => setMobileOpen(false)}>
            Careers
          </Link>
          <Link href={corporateUrl("/contact")} onClick={() => setMobileOpen(false)}>Contact Us</Link>
          <div className="pt-4 flex flex-col gap-3 pb-8">
            <Link href="/login" className="inline-flex items-center justify-start rounded-lg font-medium transition-colors hover:bg-white/5 text-gray-300 hover:text-white w-full h-11 px-6 text-base" onClick={() => setMobileOpen(false)}>
              Log In
            </Link>
            <Link href={corporateUrl("/contact")} className="inline-flex items-center justify-center rounded-lg font-medium transition-colors bg-primary text-white hover:text-white hover:bg-primary-hover w-full h-11 px-6 text-base" onClick={() => setMobileOpen(false)}>
              Book a Demo
            </Link>
          </div>
        </div>
      </div>
    </nav>
  );
}
