import Image from "next/image";
import Link from "next/link";
import { ArrowRight, BarChart3, CheckCircle2, Code2, FileText, Globe, Mail, Send, ShieldCheck, Users, Zap } from "lucide-react";
import { ChannelNavigation } from "@/components/layout/channel-navigation";
import { InteractiveEmailShowcase } from "@/components/marketing/interactive-email-showcase";
import { MarketingMotion } from "@/components/marketing/marketing-motion";
import "./textonext-home.css";
import "./email-home.css";

const features = [
  { icon: Send, title: "Email Campaigns", body: "Reach your customers with personalised email campaigns, saved drafts, and scheduled sends." },
  { icon: FileText, title: "Beautiful Templates", body: "Create reusable email templates and see exactly how your message looks on a desktop screen." },
  { icon: Users, title: "Contact Management", body: "Import your audience, organise contact lists, and build segments for relevant conversations." },
  { icon: BarChart3, title: "Delivery Insights", body: "Follow sent, delivered, bounced, and complained messages from one familiar dashboard." },
];

export default function HomePage() {
  return (
    <MarketingMotion><div className="home-light email-home">
      <header id="site-nav" className="fixed top-0 z-50 w-full border-b bg-white/95 backdrop-blur-md">
        <div className="home-container flex h-20 items-center justify-between gap-3">
          <Link href="/" aria-label="Texto home"><Image src="/texto.png" alt="Texto" width={55} height={55} /></Link>
          <ChannelNavigation marketing />
          <nav aria-label="Email navigation" className="flex items-center gap-6 text-sm font-medium">
            <Link href="#features" className="hidden md:block">Product</Link><Link href="/plans" className="hidden md:block">Pricing</Link>
            <Link href="/login" className="rounded-xl bg-[#00c896] px-4 py-2.5 text-white! hover:bg-[#00a67a]">Sign in</Link>
          </nav>
        </div>
      </header>
      <main>
        <section id="hero" className="home-hero">
          <div className="home-container"><div className="home-hero-grid">
            <div className="home-hero-text">
              <span className="home-pill"><span className="dot" /> Email Campaigns &amp; Automation</span>
              <h1>Turn Emails into<br /><span className="home-gradient-text">Business Growth</span></h1>
              <p className="home-lead">Powerful email marketing to engage customers, personalise every message, and drive sales — straight to their inbox.</p>
              <div className="home-hero-cta"><Link href="/login" className="home-btn home-btn-primary">Get Started <ArrowRight size={18} /></Link><Link href="#features" className="home-btn home-btn-outline">Explore Product</Link></div>
              <div className="home-trustline"><span>Personalised campaigns</span><span className="sep">·</span><span>Reusable templates</span><span className="sep">·</span><span>Amazon SES delivery</span></div>
              <div className="home-stats">{[{value:"Create",label:"Your message"},{value:"Connect",label:"With your audience"},{value:"Grow",label:"Your business"}].map(item => <div className="home-stat" key={item.value}><div className="home-stat-num">{item.value}</div><div className="home-stat-label">{item.label}</div></div>)}</div>
            </div>
            <div className="home-hero-visual"><InteractiveEmailShowcase /></div>
          </div></div>
        </section>
        <section id="features" className="home-section"><div className="home-container">
          <div className="home-section-head"><h2 className="home-h2">One platform. <span className="home-accent-text">Complete customer connections.</span></h2><p className="home-lead">The same Texto experience you know, built for email — connect, engage, and grow your business.</p></div>
          <div className="home-grid home-grid-4">{features.map(item => <div className="home-card is-hover" key={item.title}><div className="home-card-ic"><item.icon size={24} /></div><h3>{item.title}</h3><p>{item.body}</p></div>)}</div>
        </div></section>
        <section id="product" className="home-section bg-egg"><div className="home-container">
          <div className="home-section-head"><h2 className="home-h2">Built for scale. Designed for simplicity.</h2><p className="home-lead">Everything your team needs to create and send emails from a familiar workspace.</p></div>
          <div className="home-grid home-grid-3">{[{icon:Mail,title:"Email for every occasion",body:"Send campaigns and transactional messages from your own verified domain."},{icon:Globe,title:"Your brand, your domain",body:"Manage sending domains and authentication from your workspace."},{icon:Code2,title:"Connect your applications",body:"Use API keys and webhooks to connect email to your existing tools."}].map(item => <div className="home-card is-hover" key={item.title}><div className="home-card-ic"><item.icon size={24} /></div><h3>{item.title}</h3><p>{item.body}</p></div>)}</div>
        </div></section>
        <section className="home-section"><div className="home-container"><div className="home-split">
          <div><h2 className="home-h2">Why businesses choose <span className="home-accent-text">Texto</span></h2><p className="home-lead">Your WhatsApp and email workspaces share one familiar design, so your team can focus on connecting with customers.</p><div className="home-checks">{["Personalised messages and reusable templates","Contacts, lists, and audience segments","Campaign drafts and scheduled sending","Verified sender domains","Delivery history and suppression controls","API access and delivery webhooks"].map(text => <div className="home-check" key={text}><CheckCircle2 size={22} /><span>{text}</span></div>)}</div></div>
          <div className="home-grid home-grid-2">{[{icon:Zap,title:"Simple to send",body:"Create a message, choose your audience, and launch your campaign."},{icon:ShieldCheck,title:"Delivery controls",body:"Manage sender authentication, unsubscribes, and suppressed recipients."}].map(item => <div className="home-card" key={item.title}><div className="home-card-ic"><item.icon size={24} /></div><h3>{item.title}</h3><p>{item.body}</p></div>)}</div>
        </div></div></section>
        <section id="pricing" className="home-section bg-egg"><div className="home-container"><div className="home-price-teaser"><h2 className="home-h2">Simple, transparent pricing</h2><p className="home-lead">Find a plan that fits your audience and email volume.</p><div className="home-hero-cta justify-center"><Link href="/plans" className="home-btn home-btn-primary">View Pricing &amp; Plans <ArrowRight size={18} /></Link></div></div></div></section>
      </main>
      <footer className="footer-section py-10 text-white"><div className="home-container flex flex-wrap items-center justify-between gap-4"><p className="text-sm">Texto · Connect. Engage. Grow.</p><nav aria-label="Footer" className="flex gap-5 text-sm"><Link href="/login">Email workspace</Link><Link href="/admin/login">Admin login</Link></nav></div></footer>
    </div></MarketingMotion>
  );
}
