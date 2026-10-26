import Link from "next/link";
import { corporateUrl } from "./marketing-links";

/**
 * Marketing footer — ported from Footer.ascx. The `<style>` block from the
 * original now lives (verbatim) at the end of texto-home.css, so this is pure
 * markup. Internal links use next/link; tel/mailto/social stay as <a>.
 *
 * NOTE: `/texto.png` is a placeholder for the real `texto.svg` wordmark and the
 * Meta Business Partner badge (`~/assets/image.jpg`) — swap when available.
 */
export default function Footer() {
  return (
    <footer className="footer-section">
      <div className="ft-container">
        <div className="ft-grid">
          {/* Brand + credentials */}
          <div className="ft-brand">
            <Link href="/" className="ft-brand-logo">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src="/texto.svg"
                alt="Texto"
                style={{ width: 75, height: 75 }}
              />
            </Link>
            <p className="ft-brand-desc">
              Connect with your customers through personalised email campaigns,
              reusable templates and your own verified sending domain, with
              Amazon SES delivery.
            </p>

            <div className="ft-meta">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src="/meta-business-partner.png"
                alt="Meta Business Partner badge"
              />
              <span className="ft-meta-txt">
                <span className="t1">Meta Business Partner</span>
                <span className="t2">
                  Verified WhatsApp Business Solution Provider
                </span>
              </span>
            </div>

            <div className="ft-contact">
              <a href="tel:+919082261791">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
                </svg>
                +91 90822 61791
              </a>
              <a href="mailto:info@texto.in">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <rect width="20" height="16" x="2" y="4" rx="2" />
                  <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
                </svg>
                info@texto.in
              </a>
            </div>
          </div>

          {/* Product */}
          <div className="ft-col">
            <h4 className="ft-col-title">Product</h4>
            <div className="ft-links">
              <Link href="/#email-api">
                Email Sending API
              </Link>
              <Link href="/#email-templates">Email Templates</Link>
              <Link href="/#email-campaigns">
                Email Campaigns
              </Link>
              <Link href="/#email-integrations">Integrations</Link>
              <Link href="/#email-analytics">Analytics</Link>
            </div>
          </div>

          {/* Solutions */}
          <div className="ft-col">
            <h4 className="ft-col-title">Solutions</h4>
            <div className="ft-links">
              <Link href="/#solutions">E-commerce &amp; D2C</Link>
              <Link href="/#solutions">BFSI</Link>
              <Link href="/#solutions">Healthcare</Link>
              <Link href="/#solutions">Logistics</Link>
              <Link href="/#solutions">Travel &amp; Hospitality</Link>
              <Link href="/#solutions">EdTech</Link>
            </div>
          </div>

          {/* Company */}
          <div className="ft-col">
            <h4 className="ft-col-title">Company</h4>
            <div className="ft-links">
              <Link href={corporateUrl("/about")}>About Us</Link>
              <Link href={corporateUrl("/careers")}>Careers</Link>
              <Link href={corporateUrl("/partners")}>Partners</Link>
              <Link href={corporateUrl("/contact")}>Contact Us</Link>
            </div>
          </div>

          {/* Get started */}
          <div className="ft-col">
            <h4 className="ft-col-title">Get Started</h4>
            <div className="ft-links">
              <Link href={corporateUrl("/contact")}>Book a Demo</Link>
              <Link href="/plans">Pricing &amp; Plans</Link>
              <Link href="/login">Email Workspace</Link>
              <Link href="/login">Login</Link>
            </div>
          </div>
        </div>
      </div>

      <div className="ft-bottom">
        <div className="ft-bottom-inner">
          <p className="ft-copy">© 2026 Texto. All rights reserved.</p>
          <div className="ft-legal">
            <Link href={corporateUrl("/Privacy")}>Privacy Policy</Link>
            <Link href={corporateUrl("/terms-and-conditions")}>Terms of Service</Link>
          </div>
          <div className="ft-social">
            <a
              href="https://www.linkedin.com/company/textodigital/"
              target="_blank"
              rel="noopener noreferrer"
              title="LinkedIn"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" />
                <rect width="4" height="12" x="2" y="9" />
                <circle cx="4" cy="4" r="2" />
              </svg>
            </a>
            <a
              href="https://x.com/Texto_25?s=08"
              target="_blank"
              rel="noopener noreferrer"
              title="Twitter/X"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M22 4s-.7 2.1-2 3.4c1.6 10-9.4 17.3-18 11.6 2.2.1 4.4-.6 6-2C3 15.5.5 9.6 3 5c2.2 2.6 5.6 4.1 9 4-.9-4.2 4-6.6 7-3.8 1.1 0 3-1.2 3-1.2z" />
              </svg>
            </a>
            <a
              href="https://www.facebook.com/profile.php?id=61582906413587&sk=followers"
              target="_blank"
              rel="noopener noreferrer"
              title="Facebook"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
