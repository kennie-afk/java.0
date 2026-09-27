"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { Wordmark } from "@/components/logo";
import { CartButton, CartDrawer, CartProvider, useCart } from "@/app/welcome/cart";

// PLACEHOLDER: swap the business name, product copy and contact details for
// the real ones before presenting or going live.
const CONTACT_WHATSAPP = "254700000000"; // digits only, country code, no +
const CONTACT_EMAIL = "hello@freshferment-demo.co.ke";

export interface PublicProduct {
  id: string;
  sku: string;
  name: string;
  category: string;
  unit: string;
  chilled: boolean;
  shelfLifeHours: number;
  priceCents: number;
  inStock: number;
}

const FALLBACK: PublicProduct[] = [
  { id: "1", sku: "FML-500", name: "Plain fermented milk", category: "Plain", unit: "500ml", chilled: true, shelfLifeHours: 240, priceCents: 7000, inStock: 100 },
  { id: "2", sku: "FML-STRAW-500", name: "Strawberry fermented milk", category: "Flavoured", unit: "500ml", chilled: true, shelfLifeHours: 216, priceCents: 9000, inStock: 100 },
  { id: "3", sku: "FML-PRO-500", name: "Probiotic plus", category: "Probiotic", unit: "500ml", chilled: true, shelfLifeHours: 288, priceCents: 11000, inStock: 100 },
  { id: "4", sku: "FML-SS-250", name: "Single-serve", category: "Single-serve", unit: "250ml", chilled: true, shelfLifeHours: 216, priceCents: 5000, inStock: 100 },
];

function ksh(cents: number): string {
  return `KSh ${(cents / 100).toLocaleString("en-KE", { maximumFractionDigits: 0 })}`;
}

function familyOf(name: string): string {
  const lowered = name.toLowerCase();
  if (lowered.includes("artisanal")) return "Small-batch";
  if (lowered.includes("probiotic")) return "Probiotic";
  if (lowered.includes("single-serve")) return "Single-serve";
  if (lowered.includes("family pack")) return "Family pack";
  if (["strawberry", "mango", "vanilla"].some((f) => lowered.includes(f))) return "Flavoured";
  return "Plain";
}

function waLink(product?: PublicProduct): string {
  const message = product
    ? `Hi! I'd like to order ${product.name} (${product.unit}).`
    : "Hi! I'd like to find out more about stocking your products.";
  return `https://wa.me/${CONTACT_WHATSAPP}?text=${encodeURIComponent(message)}`;
}

export function Storefront({ products, slug }: { products: PublicProduct[]; slug?: string }) {
  return (
    <CartProvider>
      <StorefrontBody products={products} />
      <CartDrawer slug={slug} />
    </CartProvider>
  );
}

function StorefrontBody({ products }: { products: PublicProduct[] }) {
  const live = products.length > 0 ? products : FALLBACK;
  const families = useMemo(() => {
    const seen = new Set<string>();
    const ordered: string[] = [];
    for (const product of live) {
      const family = familyOf(product.name);
      if (!seen.has(family)) {
        seen.add(family);
        ordered.push(family);
      }
    }
    return ordered;
  }, [live]);

  const [active, setActive] = useState<string>("All");
  const filtered = active === "All" ? live : live.filter((p) => familyOf(p.name) === active);

  return (
    <main className="min-h-screen bg-[var(--color-canvas)]">
      {/* Sticky header */}
      <header className="sticky top-0 z-30 border-b border-[var(--color-line)] bg-[var(--color-canvas)]/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Wordmark />
          <nav className="hidden items-center gap-7 text-[0.875rem] text-[var(--color-muted)] sm:flex">
            <a href="#products" className="transition-colors hover:text-[var(--color-ink)]">Products</a>
            <a href="#why" className="transition-colors hover:text-[var(--color-ink)]">Why us</a>
            <a href="#contact" className="transition-colors hover:text-[var(--color-ink)]">Contact</a>
          </nav>
          <div className="flex items-center gap-3">
            <CartButton />
            <Link
              href="/login"
              className="rounded-full border border-[var(--color-line)] bg-white px-4 py-2 text-[0.875rem] shadow-sm transition-all hover:-translate-y-0.5 hover:border-[var(--color-accent)] hover:shadow-md"
            >
              Sign in
            </Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div
          className="pointer-events-none absolute inset-0 -z-10 opacity-[0.06]"
          style={{
            backgroundImage:
              "radial-gradient(circle at 1px 1px, var(--color-ink) 1px, transparent 0)",
            backgroundSize: "28px 28px",
          }}
        />
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-6 py-14 lg:grid-cols-2 lg:py-20">
          <div>
            <h1 className="text-[2.25rem] font-medium leading-[1.15] tracking-[-0.02em]">
              Fermented milk,
              <br />
              <span className="text-[var(--color-accent)]">always cold,</span> always fresh.
            </h1>
            <p className="mt-5 max-w-md text-[0.875rem] leading-relaxed text-[var(--color-muted)]">
              A cultured-milk drink line for retail, gyms and health shops — plain, flavoured and
              probiotic, from a single serve to a family pack. Every order is routed to a supplier
              who can actually keep it cold, not just whoever is cheapest.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a
                href={waLink()}
                target="_blank"
                rel="noreferrer"
                className="group inline-flex items-center gap-2 rounded-full bg-[var(--color-ink)] px-5 py-3 text-[0.875rem] text-white shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-lg"
              >
                <WhatsAppIcon className="h-4 w-4 transition-transform group-hover:scale-110" />
                Order on WhatsApp
              </a>
              <a
                href="#products"
                className="inline-flex items-center gap-2 rounded-full border border-[var(--color-line)] bg-white px-5 py-3 text-[0.875rem] transition-all hover:-translate-y-0.5 hover:border-[var(--color-accent)] hover:shadow-md"
              >
                Browse products
                <ArrowDown className="h-3.5 w-3.5" />
              </a>
            </div>
            <div className="mt-9 flex gap-8 border-t border-[var(--color-line)] pt-6">
              <Stat value={`${live.length}+`} label="products" />
              <Stat value="10-14" label="days fresh" />
              <Stat value="100%" label="cold chain" />
            </div>
          </div>

          <div className="relative">
            <div className="overflow-hidden rounded-2xl border border-[var(--color-line)] shadow-xl shadow-black/5">
              <Image
                src="/dairy.jpeg"
                alt="Fresh dairy at the source"
                width={736}
                height={552}
                priority
                className="h-[340px] w-full object-cover transition-transform duration-700 hover:scale-105"
              />
            </div>
            <div className="absolute -bottom-5 -left-5 rounded-xl border border-[var(--color-line)] bg-white px-4 py-3 shadow-lg">
              <p className="text-[0.75rem] text-[var(--color-faint)]">Delivered</p>
              <p className="text-[0.875rem] font-medium">Same-day, chilled</p>
            </div>
          </div>
        </div>
      </section>

      {/* Product grid */}
      <section id="products" className="mx-auto max-w-6xl px-6 py-14">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-[1.25rem] font-medium tracking-[-0.01em]">Our products</h2>
            <p className="mt-1 text-[0.875rem] text-[var(--color-muted)]">
              {filtered.length} of {live.length} shown, in stock right now.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {["All", ...families].map((family) => (
              <button
                key={family}
                onClick={() => setActive(family)}
                className={`rounded-full px-3.5 py-1.5 text-[0.875rem] transition-all ${
                  active === family
                    ? "bg-[var(--color-ink)] text-white shadow-sm"
                    : "border border-[var(--color-line)] bg-white text-[var(--color-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-ink)]"
                }`}
              >
                {family}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      </section>

      {/* Why us */}
      <section id="why" className="border-y border-[var(--color-line)] bg-[var(--color-surface)]">
        <div className="mx-auto max-w-6xl px-6 py-14">
          <h2 className="text-[1.25rem] font-medium tracking-[-0.01em]">Why buy from us</h2>
          <div className="mt-7 grid gap-5 sm:grid-cols-3">
            <WhyCard
              icon={<ColdChainIcon className="h-5 w-5" />}
              title="Real cold chain"
              body="Every order is routed to a supplier who can physically keep it cold — not just the cheapest one available."
            />
            <WhyCard
              icon={<ClockIcon className="h-5 w-5" />}
              title="Never sold stale"
              body="Products are only dispatched from suppliers who can deliver while real shelf life remains."
            />
            <WhyCard
              icon={<TruckIcon className="h-5 w-5" />}
              title="Tracked to your door"
              body="Sign in to follow every order from routed to dispatched to delivered, with real timestamps."
            />
          </div>
        </div>
      </section>

      {/* Contact */}
      <section id="contact" className="mx-auto max-w-6xl px-6 py-14">
        <div className="overflow-hidden rounded-2xl border border-[var(--color-line)] bg-gradient-to-br from-[var(--color-accent-soft)] to-[var(--color-amber-soft)] p-8 sm:p-10">
          <h2 className="text-[1.25rem] font-medium tracking-[-0.01em]">
            Want to stock our products in your shop, gym or supermarket?
          </h2>
          <p className="mt-2 max-w-2xl text-[0.875rem] leading-relaxed text-[var(--color-muted)]">
            Get in touch and we&apos;ll set you up with an account so you can order directly, see
            your order history, and track every delivery until it arrives.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <a
              href={waLink()}
              target="_blank"
              rel="noreferrer"
              className="group inline-flex items-center gap-2 rounded-full bg-[var(--color-ink)] px-5 py-3 text-[0.875rem] text-white shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-lg"
            >
              <WhatsAppIcon className="h-4 w-4 transition-transform group-hover:scale-110" />
              WhatsApp us
            </a>
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              className="inline-flex items-center gap-2 rounded-full border border-[var(--color-line)] bg-white px-5 py-3 text-[0.875rem] transition-all hover:-translate-y-0.5 hover:shadow-md"
            >
              {CONTACT_EMAIL}
            </a>
          </div>
        </div>
      </section>

      <footer className="mx-auto max-w-6xl px-6 py-8 text-[0.75rem] text-[var(--color-faint)]">
        © {new Date().getFullYear()} FreshFerm. Built for real fridges, not just a warehouse.
      </footer>

      {/* Floating WhatsApp button */}
      <a
        href={waLink()}
        target="_blank"
        rel="noreferrer"
        aria-label="Chat on WhatsApp"
        className="fixed bottom-6 right-6 z-40 flex items-center gap-2 rounded-full bg-[#25D366] px-5 py-3.5 text-[0.875rem] text-white shadow-lg shadow-black/20 transition-all hover:scale-105 hover:shadow-xl"
      >
        <WhatsAppIcon className="h-5 w-5" />
        <span className="hidden sm:inline">Chat with us</span>
      </a>
    </main>
  );
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div>
      <p className="text-[1rem] font-medium tabular-nums">{value}</p>
      <p className="text-[0.75rem] text-[var(--color-faint)]">{label}</p>
    </div>
  );
}

function ProductCard({ product }: { product: PublicProduct }) {
  const cart = useCart();
  const inCart = cart.lines.find((line) => line.product.id === product.id);
  const justAdded = cart.lastAdded === product.id;

  return (
    <div className="group relative overflow-hidden rounded-2xl border border-[var(--color-line)] bg-white p-5 transition-all hover:-translate-y-1 hover:border-[var(--color-accent)]/40 hover:shadow-xl hover:shadow-black/5">
      <div className="flex items-start justify-between">
        <span className="rounded-full bg-[var(--color-accent-soft)] px-2.5 py-0.5 text-[0.75rem] text-[var(--color-accent)]">
          {familyOf(product.name)}
        </span>
        {product.chilled && (
          <span className="flex items-center gap-1 text-[0.75rem] text-[var(--color-faint)]" title="Requires cold chain">
            <ColdChainIcon className="h-3.5 w-3.5" />
            Chilled
          </span>
        )}
      </div>
      <p className="mt-4 text-[1rem] font-medium leading-snug tracking-[-0.01em]">
        {product.name}
      </p>
      <p className="mt-1 text-[0.875rem] text-[var(--color-muted)]">{product.unit}</p>
      <div className="mt-4 flex items-baseline justify-between">
        <p className="text-[1rem] font-medium tabular-nums text-[var(--color-ink)]">
          {ksh(product.priceCents)}
        </p>
        <p className="text-[0.75rem] text-[var(--color-faint)]">In stock</p>
      </div>
      <button
        onClick={() => cart.add(product)}
        className={`mt-4 flex w-full items-center justify-center gap-2 rounded-full py-2.5 text-[0.875rem] transition-all ${
          justAdded
            ? "bg-[var(--color-accent)] text-white"
            : "bg-[var(--color-ink)] text-white group-hover:-translate-y-0.5"
        }`}
      >
        {justAdded ? "Added" : inCart ? `In cart · ${inCart.quantity}` : "Add to cart"}
      </button>
      <a
        href={waLink(product)}
        target="_blank"
        rel="noreferrer"
        className="mt-2 flex w-full items-center justify-center gap-1.5 py-1 text-[0.75rem] text-[var(--color-faint)] transition-colors hover:text-[var(--color-ink)]"
      >
        or order on WhatsApp
      </a>
    </div>
  );
}

function WhyCard({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) {
  return (
    <div className="rounded-xl border border-[var(--color-line)] bg-[var(--color-canvas)] p-5 transition-all hover:border-[var(--color-accent)]/40 hover:shadow-md">
      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
        {icon}
      </div>
      <p className="mt-3 text-[1rem] font-medium">{title}</p>
      <p className="mt-1.5 text-[0.875rem] leading-relaxed text-[var(--color-muted)]">{body}</p>
    </div>
  );
}

function WhatsAppIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M17.5 14.4c-.3-.1-1.7-.8-1.9-.9-.3-.1-.5-.1-.7.1-.2.3-.7.9-.9 1.1-.2.2-.3.2-.6.1-.9-.4-1.8-1-2.6-1.8-.7-.7-1.3-1.5-1.7-2.3-.1-.2 0-.4.1-.5.2-.2.5-.5.6-.7.1-.2.1-.4 0-.6-.1-.2-.6-1.5-.8-2-.2-.5-.4-.4-.6-.4h-.5c-.2 0-.5.1-.7.3-.2.3-.9.9-.9 2.2s.9 2.6 1.1 2.8c.1.2 1.7 2.7 4.2 3.8 2.5 1.1 2.5.7 2.9.7.5 0 1.5-.6 1.7-1.2.2-.6.2-1.1.1-1.2-.1-.1-.2-.2-.4-.3z" />
      <path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l4.9-1.3A10 10 0 1 0 12 2Zm0 18.2a8.2 8.2 0 0 1-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 20.2 12 8.2 8.2 0 0 1 12 20.2Z" />
    </svg>
  );
}

function ColdChainIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <path d="M12 2v20M4.5 6.5l15 11M19.5 6.5l-15 11" strokeLinecap="round" />
    </svg>
  );
}

function ClockIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function TruckIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <path d="M2 7h11v9H2z" strokeLinejoin="round" />
      <path d="M13 10h4l4 3v3h-8z" strokeLinejoin="round" />
      <circle cx="6" cy="18" r="1.6" />
      <circle cx="17" cy="18" r="1.6" />
    </svg>
  );
}

function ArrowDown({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={className} aria-hidden="true">
      <path d="M12 4v16M6 14l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
