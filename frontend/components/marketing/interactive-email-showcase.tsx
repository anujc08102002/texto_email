"use client";

import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { motion, useInView, useMotionValue, useReducedMotion, useSpring, useTransform } from "motion/react";
import { ArrowLeft, ArrowRight, Check, Mail, MoveUpRight, Pause, Play, RotateCcw } from "lucide-react";
import { EmailDesktopPreview } from "@/components/templates/email-desktop-preview";

const examples = [
  { label: "Welcome email", subject: "A great connection starts here", eyebrow: "A FRESH START", heading: "Hello, Alex.<br>Your next chapter starts here.", body: "Make every message feel personal. Welcome your customers with an email made just for them.", action: "Discover what’s new", color: "#00a67a" },
  { label: "Product launch", subject: "Meet your next favourite thing", eyebrow: "SOMETHING NEW", heading: "Made for you.<br>Ready to discover.", body: "A thoughtful launch deserves a beautiful introduction. Give your audience a first look at what’s next.", action: "Take a first look", color: "#4f46e5" },
  { label: "Order update", subject: "Your order is on its way", eyebrow: "YOU’RE ALL SET", heading: "Good things<br>are on their way.", body: "Keep customers in the loop with clear, timely updates. Your order is packed and ready for its next stop.", action: "View your order", color: "#0284c7" },
];
const clamp = (value: number, limit: number) => Math.max(-limit, Math.min(limit, value));

export function InteractiveEmailShowcase() {
  const [selected, setSelected] = useState(0);
  const [playing, setPlaying] = useState(true);
  const stage = useRef<HTMLDivElement>(null);
  const grab = useRef<{ id: number; x: number; y: number } | null>(null);
  const inView = useInView(stage, { amount: 0.2 });
  const reduced = useReducedMotion();
  const tiltX = useMotionValue(0);
  const tiltY = useMotionValue(0);
  const moveX = useMotionValue(0);
  const moveY = useMotionValue(0);
  const spring = { stiffness: 145, damping: 21, mass: 0.8 };
  const rotateX = useSpring(tiltX, spring);
  const rotateY = useSpring(tiltY, spring);
  const x = useSpring(moveX, spring);
  const y = useSpring(moveY, spring);
  const glareX = useTransform(rotateY, [-26, 26], ["-30%", "30%"]);
  const example = examples[selected];

  useEffect(() => {
    if (!playing || reduced || !inView) return;
    const timer = window.setInterval(() => {
      if (!document.hidden && !grab.current) setSelected(value => (value + 1) % examples.length);
    }, 6500);
    return () => window.clearInterval(timer);
  }, [playing, reduced, inView]);

  function reset() { tiltX.set(0); tiltY.set(0); moveX.set(0); moveY.set(0); }
  function pointerDown(event: PointerEvent<HTMLDivElement>) {
    if (event.button !== 0 || reduced) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    grab.current = { id: event.pointerId, x: event.clientX, y: event.clientY };
    event.currentTarget.dataset.dragging = "true";
  }
  function pointerMove(event: PointerEvent<HTMLDivElement>) {
    if (reduced) return;
    if (grab.current) {
      const dx = event.clientX - grab.current.x, dy = event.clientY - grab.current.y;
      tiltX.set(clamp(-dy * 0.16, 20)); tiltY.set(clamp(dx * 0.19, 26));
      moveX.set(clamp(dx * 0.13, 30)); moveY.set(clamp(dy * 0.1, 22));
    } else if (event.pointerType === "mouse") {
      const rect = event.currentTarget.getBoundingClientRect();
      tiltX.set(-((event.clientY - rect.top) / rect.height - 0.5) * 7);
      tiltY.set(((event.clientX - rect.left) / rect.width - 0.5) * 9);
    }
  }
  function release(event: PointerEvent<HTMLDivElement>) {
    if (grab.current?.id !== event.pointerId) return;
    grab.current = null;
    event.currentTarget.dataset.dragging = "false";
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    reset();
  }
  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (reduced || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home", "Escape"].includes(event.key)) return;
    event.preventDefault();
    if (event.key === "ArrowLeft") tiltY.set(clamp(tiltY.get() - 6, 26));
    if (event.key === "ArrowRight") tiltY.set(clamp(tiltY.get() + 6, 26));
    if (event.key === "ArrowUp") tiltX.set(clamp(tiltX.get() + 6, 20));
    if (event.key === "ArrowDown") tiltX.set(clamp(tiltX.get() - 6, 20));
    if (["Home", "Escape"].includes(event.key)) reset();
  }

  const html = `<style>body{padding:clamp(12px,3vw,28px)!important} .demo-mail{max-width:470px;margin:auto;text-align:center}.demo-mail small{font-size:9px;letter-spacing:.14em;font-weight:700;color:${example.color}}.demo-mail h2{font-size:clamp(20px,4vw,30px);line-height:1.15;letter-spacing:-.04em;color:#182335;margin:12px 0}.demo-mail p{font-size:clamp(10px,2vw,12px);line-height:1.7;color:#64748b;max-width:310px;margin:0 auto}.demo-mail .cta{display:inline-block;margin-top:16px;padding:10px 20px;border-radius:8px;background:${example.color};color:#fff;font-size:11px;font-weight:bold}.demo-mail footer{margin-top:14px;font-size:9px;color:#94a3b8}@media(max-width:400px){.demo-mail h2{font-size:18px;margin:8px 0}.demo-mail p{font-size:10px;line-height:1.5}.demo-mail .cta{font-size:10px;padding:8px 14px;margin-top:10px}.demo-mail footer{display:none}}</style><div class="demo-mail"><small>${example.eyebrow}</small><h2>${example.heading}</h2><p>${example.body}</p><span class="cta">${example.action} →</span><footer>TEXTO · A little more connected.</footer></div>`;
  return (
    <div className={playing && !reduced ? "email-showcase" : "email-showcase is-paused"} ref={stage}>
      <div className="email-showcase-halo" aria-hidden="true" />
      <motion.div className="email-device-entrance" initial={reduced ? false : { opacity: 0, y: 38, scale: 0.96 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ type: "spring", stiffness: 70, damping: 18, delay: 0.18 }}>
        <div className={playing && !reduced ? "email-device-float" : "email-device-float is-still"}>
          <motion.div role="group" aria-label="Interactive email computer. Drag to move and rotate; use arrow keys to tilt, Escape to reset." tabIndex={0}
            className="email-device-grab" style={{ rotateX, rotateY, x, y }}
            onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={release} onPointerCancel={release} onLostPointerCapture={release}
            onPointerLeave={() => { if (!grab.current) reset(); }} onKeyDown={keyDown} onBlur={reset}>
            <EmailDesktopPreview html={html} subject={example.subject} sender="Texto Studio" title="Example email on a desktop computer" />
            <motion.div className="email-device-glare" style={{ x: glareX }} aria-hidden="true" />
          </motion.div>
        </div>
        <div className="email-proof email-proof-top" aria-hidden="true"><span className="email-proof-icon"><Mail size={18} /></span><span><small>Every message</small><strong>A personal touch</strong></span></div>
        <div className="email-proof email-proof-bottom" aria-hidden="true"><span className="email-proof-icon"><Check size={18} /></span><span><small>Your brand</small><strong>Made for the inbox</strong></span></div>
      </motion.div>
      <div className="email-demo-controls">
        <div className="email-demo-tabs" role="group" aria-label="Example emails">{examples.map((item, index) => <button type="button" key={item.label} aria-pressed={selected === index} onClick={() => { setSelected(index); setPlaying(false); }}>{item.label}</button>)}</div>
        <button type="button" className="email-demo-icon" disabled={!!reduced} aria-label={reduced ? "Animations disabled by reduced-motion preference" : playing ? "Pause email animation" : "Play email animation"} onClick={() => setPlaying(value => !value)}>{playing ? <Pause size={15} /> : <Play size={15} />}</button>
        <button type="button" className="email-demo-icon" aria-label="Reset computer position" onClick={reset}><RotateCcw size={15} /></button>
      </div>
      <p className="email-demo-hint"><MoveUpRight size={13} aria-hidden="true" /> Drag the computer to move &amp; rotate <span>·</span> Example emails</p>
      <div className="email-tilt-controls" aria-label="Computer rotation controls"><button type="button" disabled={!!reduced} onClick={() => tiltY.set(clamp(tiltY.get() - 8, 26))} aria-label="Rotate computer left"><ArrowLeft size={14} /></button><button type="button" disabled={!!reduced} onClick={() => tiltY.set(clamp(tiltY.get() + 8, 26))} aria-label="Rotate computer right"><ArrowRight size={14} /></button></div>
    </div>
  );
}
