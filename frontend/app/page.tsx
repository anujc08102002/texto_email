import type { Metadata } from "next";
import Link from "next/link";
import { Inter } from "next/font/google";
import { InteractiveEmailShowcase } from "@/components/marketing/interactive-email-showcase";
import { MarketingMotion } from "@/components/marketing/marketing-motion";
import MarketingNavbar from "@/components/marketing/marketing-navbar";
import MarketingFooter from "@/components/marketing/marketing-footer";
import EmailTrustMarquee from "@/components/marketing/email-trust-marquee";
import WhatsAppFAB from "@/components/marketing/whatsapp-fab";
import { corporateUrl } from "@/components/marketing/marketing-links";
import "./textonext-home.css";
import "./email-home.css";

const inter = Inter({ subsets: ["latin"], weight: ["400", "500", "600", "700", "800"], variable: "--font-inter", display: "swap" });
export const metadata: Metadata = { title: "Email Campaigns & Automation", description: "Texto email campaigns, templates, verified sending domains and Amazon SES delivery." };

export default function Home() {
  return (
    <MarketingMotion><div className={`home-light email-home ${inter.variable}`}><MarketingNavbar /><main>


      {/* ===================== 1. HERO ===================== */}
      <section id="hero" className="home-hero">
        <div className="home-container">
          <div className="home-hero-grid">
            {/* Text column */}
            <div className="home-hero-text">
              <span className="home-pill">
                <span className="dot"></span> Email Campaigns &amp; Automation
              </span>

              <h1 id="hero-title">
                <span className="email-hero-line">Turn</span>
                <span className="email-hero-line">Email Campaigns</span>
                <span className="email-hero-line">into</span>
                <span className="home-gradient-text">Business Growth</span>
              </h1>

              <p id="hero-sub" className="home-lead">
                Powerful email marketing to engage customers, personalise every message,
                and drive sales — straight to their inbox.
              </p>

              <div id="hero-cta" className="home-hero-cta">
                <Link href={corporateUrl("/contact")} className="home-btn home-btn-primary">
                  Book a Demo
                  <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
                </Link>
                <Link href="#product" className="home-btn home-btn-outline">
                  Explore Product
                </Link>
              </div>

              <div id="hero-trust" className="home-trustline">
                <span>Personalised email campaigns</span>
                <span className="sep">·</span>
                <span>Amazon SES delivery</span>
                <span className="sep">·</span>
                <span>Your brand. Your sending domain.</span>
              </div>

              <div id="hero-stats" className="home-stats">
                <div className="home-stat">
                  <div className="home-stat-num">SES</div>
                  <div className="home-stat-label">Email Delivery</div>
                </div>
                <div className="home-stat">
                  <div className="home-stat-num">API</div>
                  <div className="home-stat-label">Connected Applications</div>
                </div>
                <div className="home-stat">
                  <div className="home-stat-num">DNS</div>
                  <div className="home-stat-label">Verified Sending</div>
                </div>
              </div>
            </div>

            {/* Visual column: interactive email computer with grab-to-tilt */}
            <div className="home-hero-visual">
              <InteractiveEmailShowcase />
            </div>
          </div>
        </div>
      </section>

      {/* ===================== 2. TRUST MARQUEE ===================== */}
      <EmailTrustMarquee />

      {/* ===================== 3. FEATURES ===================== */}
      <section id="features" className="home-section">
        <div className="home-container">
          <div className="home-section-head" data-reveal="fade">
            <h2 className="home-h2">
              One platform. <span className="home-accent-text">Complete customer connections.</span>
            </h2>
            <p className="home-lead">
              Everything you need for email campaigns and customer communication — connect, engage, and grow
              your business with messages made for the inbox.
            </p>
          </div>

          <div className="home-grid home-grid-4">
            <div className="home-card is-hover" data-reveal>
              <div className="home-card-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z" /></svg>
              </div>
              <h3>Personalise Campaigns</h3>
              <p>Reach your audience with personalised campaigns, saved drafts and scheduled sends.</p>
            </div>
            <div className="home-card is-hover" data-reveal>
              <div className="home-card-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
              </div>
              <h3>Reusable Templates</h3>
              <p>Create branded email templates and preview every message on a computer screen.</p>
            </div>
            <div className="home-card is-hover" data-reveal>
              <div className="home-card-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17" /><polyline points="16 7 22 7 22 13" /></svg>
              </div>
              <h3>Increase Engagement</h3>
              <p>Keep customers informed with relevant email messages, updates and promotions.</p>
            </div>
            <div className="home-card is-hover" data-reveal>
              <div className="home-card-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="4" y="4" width="16" height="16" rx="2" ry="2" /><rect x="9" y="9" width="6" height="6" /><line x1="9" y1="1" x2="9" y2="4" /><line x1="15" y1="1" x2="15" y2="4" /><line x1="9" y1="20" x2="9" y2="23" /><line x1="15" y1="20" x2="15" y2="23" /><line x1="20" y1="9" x2="23" y2="9" /><line x1="20" y1="14" x2="23" y2="14" /><line x1="1" y1="9" x2="4" y2="9" /><line x1="1" y1="14" x2="4" y2="14" /></svg>
              </div>
              <h3>Organised Audiences</h3>
              <p>Organise contacts into lists and segments for more relevant customer communication.</p>
            </div>
          </div>
        </div>
      </section>

      {/* ===================== 4. PRODUCT CAPABILITIES ===================== */}
      <section id="product" className="home-section bg-egg">
        <div className="home-container">
          <div className="home-section-head" data-reveal="fade">
            <h2 className="home-h2">Built for scale. Designed for simplicity.</h2>
            <p className="home-lead">
              Powerful enterprise features delivered through an intuitive interface your whole team
              will love to use.
            </p>
          </div>

          <div className="home-bento">
            <Link id="email-api" href="/api-keys" className="home-card home-card-feature" data-reveal>
              <div className="home-card-feature-main">
                <span className="home-card-tag">Most popular</span>
                <div className="home-card-ic">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="4" width="20" height="16" rx="2" /><path d="m22 7-10 6L2 7" /></svg>
                </div>
                <h3>Email Sending API</h3>
                <p>Connect transactional and campaign email with your applications — using Amazon SES delivery and your verified sending domain.</p>
                <ul className="home-card-list">
                  <li><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5" /></svg>Verified sender domains &amp; DKIM authentication</li>
                  <li><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5" /></svg>Personalised messages &amp; reusable templates</li>
                  <li><svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5" /></svg>Delivery history, bounces &amp; suppressions</li>
                </ul>
                <span className="home-card-link">
                  Explore the API
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
                </span>
              </div>
              <div className="home-card-feature-art" aria-hidden="true">
                <span className="home-card-feature-glow"></span>
                <svg viewBox="0 0 24 24" width="120" height="120" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="4" width="20" height="16" rx="2" /><path d="m22 7-10 6L2 7" /></svg>
              </div>
            </Link>
            <div className="home-bento-rest">
              <Link id="email-templates" href="/templates" className="home-card" data-reveal>
                <div className="home-card-ic">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 8V4H8" /><rect width="16" height="12" x="4" y="8" rx="2" /><path d="M2 14h2" /><path d="M20 14h2" /><path d="M15 13v2" /><path d="M9 13v2" /></svg>
                </div>
                <h3>Email Templates</h3>
                <p>Create reusable branded emails with variables and a desktop preview.</p>
                <span className="home-card-link">
                  Learn more
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
                </span>
              </Link>
              <Link id="email-campaigns" href="/campaigns" className="home-card" data-reveal>
                <div className="home-card-ic">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="22" x2="11" y1="2" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" /></svg>
                </div>
                <h3>Email Campaigns</h3>
                <p>Create personalised email campaigns — notifications, updates, and promotions — for your contact lists.</p>
                <span className="home-card-link">
                  Learn more
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
                </span>
              </Link>
              <Link id="email-integrations" href="/webhooks" className="home-card" data-reveal>
                <div className="home-card-ic">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" /><line x1="8.59" x2="15.42" y1="13.51" y2="17.49" /><line x1="15.41" x2="8.59" y1="6.51" y2="10.49" /></svg>
                </div>
                <h3>Deep Integrations</h3>
                <p>Connect your applications through API keys and signed delivery webhooks.</p>
                <span className="home-card-link">
                  Learn more
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
                </span>
              </Link>
              <Link id="email-analytics" href="/analytics" className="home-card" data-reveal>
                <div className="home-card-ic">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" x2="18" y1="20" y2="10" /><line x1="12" x2="12" y1="20" y2="4" /><line x1="6" x2="6" y1="20" y2="14" /></svg>
                </div>
                <h3>Real-time Analytics</h3>
                <p>Follow delivery, bounce and complaint events from one dashboard.</p>
                <span className="home-card-link">
                  Learn more
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
                </span>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ===================== 5. INDUSTRIES / SOLUTIONS ===================== */}
      <section id="solutions" className="home-section">
        <div className="home-container">
          <div className="home-section-head" data-reveal="fade">
            <h2 className="home-h2">Solutions for every customer-first industry</h2>
            <p className="home-lead">
              Tailored workflows and features designed to meet the unique challenges of your sector.
            </p>
          </div>

          <div className="home-grid home-grid-3 home-ind-grid">
            <Link href="#solutions" className="home-ind" data-reveal>
              <div className="home-ind-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" /><path d="M3 6h18" /><path d="M16 10a4 4 0 0 1-8 0" /></svg>
              </div>
              <div className="home-ind-body">
                <h3>E-commerce &amp; D2C</h3>
                <p>Promotions, order notifications and customer updates by email.</p>
              </div>
              <span className="home-ind-arrow"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg></span>
            </Link>
            <Link href="#solutions" className="home-ind" data-reveal>
              <div className="home-ind-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="3" x2="21" y1="22" y2="22" /><line x1="6" x2="6" y1="18" y2="11" /><line x1="10" x2="10" y1="18" y2="11" /><line x1="14" x2="14" y1="18" y2="11" /><line x1="18" x2="18" y1="18" y2="11" /><polygon points="12 2 20 7 4 7" /></svg>
              </div>
              <div className="home-ind-body">
                <h3>BFSI</h3>
                <p>Transaction notifications, account updates and payment reminders.</p>
              </div>
              <span className="home-ind-arrow"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg></span>
            </Link>
            <Link href="#solutions" className="home-ind" data-reveal>
              <div className="home-ind-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M11 2v2" /><path d="M5 2v2" /><path d="M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1" /><path d="M8 15a6 6 0 0 0 12 0v-3" /><circle cx="20" cy="10" r="2" /></svg>
              </div>
              <div className="home-ind-body">
                <h3>Healthcare</h3>
                <p>Appointment notifications, reminders and patient communications by email.</p>
              </div>
              <span className="home-ind-arrow"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg></span>
            </Link>
            <Link href="#solutions" className="home-ind" data-reveal>
              <div className="home-ind-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2" /><path d="M15 18H9" /><path d="M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14" /><circle cx="17" cy="18" r="2" /><circle cx="7" cy="18" r="2" /></svg>
              </div>
              <div className="home-ind-body">
                <h3>Logistics</h3>
                <p>Shipment notifications, delivery updates and customer confirmations.</p>
              </div>
              <span className="home-ind-arrow"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg></span>
            </Link>
            <Link href="#solutions" className="home-ind" data-reveal>
              <div className="home-ind-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z" /></svg>
              </div>
              <div className="home-ind-body">
                <h3>Travel &amp; Hospitality</h3>
                <p>Booking confirmations, travel updates and guest communications.</p>
              </div>
              <span className="home-ind-arrow"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg></span>
            </Link>
            <Link href="#solutions" className="home-ind" data-reveal>
              <div className="home-ind-ic">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z" /><path d="M6 12v5c3 3 9 3 12 0v-5" /></svg>
              </div>
              <div className="home-ind-body">
                <h3>EdTech</h3>
                <p>Admissions, fee reminders and learner engagement at scale.</p>
              </div>
              <span className="home-ind-arrow"><svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg></span>
            </Link>
          </div>
        </div>
      </section>

      {/* ===================== 6. HOW IT WORKS ===================== */}
      <section id="how-it-works" className="home-section bg-egg">
        <div className="home-container">
          <div className="home-section-head" data-reveal="fade">
            <h2 className="home-h2">
              Get started in <span className="home-accent-text">simple steps</span>
            </h2>
            <p className="home-lead">
              From workspace setup to a verified sending domain and your first email — follow
              a clear path through every step.
            </p>
          </div>

          <div className="home-steps home-grid home-grid-5">
            <div className="home-steps-line"></div>
            <div className="home-step" data-reveal>
              <div className="home-step-num">01</div>
              <h3>Workspace &amp; Account Setup</h3>
              <p>Get your Texto workspace provisioned and configure your organisation profile.</p>
            </div>
            <div className="home-step" data-reveal>
              <div className="home-step-num">02</div>
              <h3>Verify Your Domain</h3>
              <p>Publish your domain authentication records and verify your sending identity.</p>
            </div>
            <div className="home-step" data-reveal>
              <div className="home-step-num">03</div>
              <h3>Create Your Template</h3>
              <p>Design a reusable email, add variables and check the desktop preview.</p>
            </div>
            <div className="home-step" data-reveal>
              <div className="home-step-num">04</div>
              <h3>Connect &amp; Test</h3>
              <p>Connect your application and test email delivery and signed webhooks.</p>
            </div>
            <div className="home-step" data-reveal>
              <div className="home-step-num">05</div>
              <h3>Go Live &amp; Scale</h3>
              <p>Launch your campaigns, follow delivery activity and refine your messages.</p>
            </div>
          </div>

          <div className="home-cta-band" data-reveal="fade">
            <div className="home-cta-band-text">
              <h3>Ready to get started?</h3>
              <p>Bring your email campaigns and customer connections together with Texto.</p>
            </div>
            <Link href={corporateUrl("/contact")} className="home-btn home-btn-primary home-btn-lg">
              Get Started Now
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
            </Link>
          </div>
        </div>
      </section>

      {/* ===================== 7. WHY TEXTO ===================== */}
      <section id="why" className="home-section">
        <div className="home-container">
          <div className="home-split">
            <div data-reveal="left">
              <h2 className="home-h2">
                Why businesses choose <span className="home-accent-text">Texto</span>
              </h2>
              <p className="home-lead">
                We combine enterprise-grade power with consumer-grade simplicity to deliver the best
                email campaign experience.
              </p>

              <div className="home-checks">
                {[
                  "Personalised email and reusable templates",
                  "Contacts, lists and audience segments",
                  "Campaign drafts and scheduled sending",
                  "Your brand and verified sending domain",
                  "Delivery history and customer insights",
                  "Suppression and unsubscribe controls",
                ].map((t) => (
                  <div className="home-check" key={t}>
                    <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><path d="m9 12 2 2 4-4" /></svg>
                    <span>{t}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Performance / insights card */}
            <div data-reveal="right">
              <div className="home-metrics">
                <div className="home-metrics-head">
                  <span className="home-metrics-title"><span className="home-metrics-live"></span> Example performance</span>
                  <span className="home-metrics-range">Last 7 days</span>
                </div>

                <div className="home-metrics-hero">
                  <div>
                    <div className="home-metrics-hero-lbl">Emails submitted</div>
                    <div className="home-metrics-hero-val">2.3K</div>
                  </div>
                  <span className="home-metrics-trend">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M7 17 17 7" /><path d="M7 7h10v10" /></svg>
                    12.4%
                  </span>
                </div>

                <div className="home-metrics-chart">
                  <div className="home-metrics-chart-head">
                    <span>Message volume</span>
                    <span className="home-metrics-chart-tag">Mon – Sun</span>
                  </div>
                  <div className="home-chart-bars">
                    <i style={{ height: "40%" }}></i>
                    <i style={{ height: "62%" }}></i>
                    <i style={{ height: "32%" }}></i>
                    <i style={{ height: "80%" }}></i>
                    <i style={{ height: "52%" }}></i>
                    <i className="hot" style={{ height: "—" }}></i>
                    <i style={{ height: "70%" }}></i>
                  </div>
                </div>

                <div className="home-metrics-tiles">
                  <div className="home-metrics-tile">
                    <div className="lbl">Templates</div>
                    <div className="val">12<span> saved</span></div>
                  </div>
                  <div className="home-metrics-tile">
                    <div className="lbl">Delivery</div>
                    <div className="val">98%</div>
                  </div>
                  <div className="home-metrics-tile">
                    <div className="lbl">Open rate</div>
                    <div className="val">—</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ===================== 8. TESTIMONIALS ===================== */}
      <section id="testimonials" className="home-section bg-egg">
        <div className="home-container">
          <div className="home-section-head" data-reveal="fade">
            <span className="home-section-label">From the Texto community</span>
            <h2 className="home-h2">Teams that grew with Texto</h2>
          </div>

          <div className="home-grid home-grid-2" style={{ maxWidth: 860, margin: "0 auto" }}>
            <div className="home-quote" data-reveal>
              <div className="home-quote-mark">&#8220;</div>
              <p>Texto made it effortless to reach our customers on WhatsApp. Setup was quick and our team was up and running in days.</p>
              <div className="home-quote-author">
                <div className="home-avatar">ML</div>
                <div>
                  <div className="nm">Macron Logistics</div>
                  <div className="rl">Logistics</div>
                </div>
              </div>
            </div>
            <div className="home-quote" data-reveal>
              <div className="home-quote-mark">&#8220;</div>
              <p>With Texto we run our WhatsApp campaigns and customer support from one place — reliable delivery and a platform our team actually enjoys using.</p>
              <div className="home-quote-author">
                <div className="home-avatar">TX</div>
                <div>
                  <div className="nm">Tionix</div>
                  <div className="rl">Software &amp; IT services</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ===================== 9. PRICING TEASER ===================== */}
      <section id="pricing" className="home-section">
        <div className="home-container">
          <div className="home-price-teaser" data-reveal="fade">
            <h2 className="home-h2">Simple, transparent pricing</h2>
            <p className="home-lead">
              Choose a plan that matches your email volume, sending domains and integrations.
              Explore the available features and limits for your workspace.
            </p>
            <div className="home-hero-cta" style={{ justifyContent: "center", marginTop: 28 }}>
              <Link href="/plans" className="home-btn home-btn-primary">
                View Pricing &amp; Plans
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ===================== 10. FINAL CTA ===================== */}
      <section id="cta-final" className="home-section home-cta-final">
        <div className="home-container" data-reveal="fade">
          <h2>
            Ready to transform your
            <br />
            customer connections?
          </h2>
          <p className="home-lead">
            Bring your campaigns, templates and customer communication together with Texto.
          </p>
          <div className="home-hero-cta">
            <Link href={corporateUrl("/contact")} className="home-btn home-btn-primary home-btn-lg">
              Book a Demo
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></svg>
            </Link>
            <Link href="/plans" className="home-btn home-btn-outline home-btn-lg">See Pricing</Link>
          </div>
        </div>
      </section>
    </main><MarketingFooter /><WhatsAppFAB /></div></MarketingMotion>
  );
}
