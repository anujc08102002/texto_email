"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { useReducedMotion } from "motion/react";

/** Progressive enhancement: content stays visible without JavaScript or with reduced motion. */
export function MarketingMotion({ children }: { children: ReactNode }) {
  const root = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  useEffect(() => {
    if (reduced || !root.current) return;
    const animations: Animation[] = [];
    const entrance = root.current.querySelectorAll(".home-hero-text > *");
    entrance.forEach((element, index) => {
      animations.push(element.animate([{ opacity: 0, transform: "translateY(22px)" }, { opacity: 1, transform: "translateY(0)" }], { duration: 650, delay: 70 + index * 95, easing: "cubic-bezier(.22,1,.36,1)", fill: "backwards" }));
    });
    const cards = root.current.querySelectorAll<HTMLElement>("[data-reveal], .home-check");
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.classList.remove("email-reveal-waiting");
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -24px 0px" });
    cards.forEach((element, index) => {
      element.classList.add("email-reveal");
      element.style.setProperty("--reveal-delay", `${index % 4 * 65}ms`);
      if (element.getBoundingClientRect().top > window.innerHeight * 0.95) element.classList.add("email-reveal-waiting");
      observer.observe(element);
    });
    return () => { observer.disconnect(); animations.forEach(animation => animation.cancel()); cards.forEach(element => element.classList.remove("email-reveal-waiting")); };
  }, [reduced]);
  return <div className="email-marketing" ref={root}>{children}</div>;
}
