import Link from "next/link";
import Image from "next/image";
import { buttonClass, secondaryButtonClass } from "@/components/ui";
import { Wordmark } from "@/components/logo";

// PLACEHOLDER: swap the business name, product copy and contact details for
// the real ones before presenting or going live — everything here is written
// for a fermented-milk drink business as a stand-in.
const CONTACT_WHATSAPP = "254700000000"; // digits only, country code, no +
const CONTACT_EMAIL = "hello@freshferment-demo.co.ke";

const PRODUCTS = [
  { name: "Plain fermented milk", sizes: "500ml, 1L, 2L family pack" },
  { name: "Strawberry, mango & vanilla", sizes: "500ml" },
  { name: "Probiotic plus", sizes: "500ml" },
  { name: "Single-serve", sizes: "250ml" },
];

export default function WelcomePage() {
  return (
    <main className="min-h-screen bg-[var(--color-canvas)]">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6">
        <Wordmark />
        <Link href="/login" className={secondaryButtonClass}>
          Sign in
        </Link>
      </header>

      <section className="mx-auto grid max-w-6xl items-center gap-12 px-6 py-10 lg:grid-cols-2">
        <div>
          <h1 className="text-[2.5rem] font-semibold leading-[1.1] tracking-[-0.03em]">
            Fermented milk,
            <br />
            always cold, always fresh.
          </h1>
          <p className="mt-5 max-w-md text-[0.9375rem] leading-relaxed text-[var(--color-muted)]">
            A cultured-milk drink line for retail, gyms and health shops — plain, flavoured and
            probiotic, in sizes from a single serve to a family pack. Every order is routed
            through a supplier who can actually keep it cold and deliver it with real shelf life
            left, not just whoever is cheapest.
          </p>
          <div className="mt-8 flex gap-3">
            <a href={`https://wa.me/${CONTACT_WHATSAPP}`} className={buttonClass}>
              Chat on WhatsApp to order
            </a>
            <a href={`mailto:${CONTACT_EMAIL}`} className={secondaryButtonClass}>
              Email us
            </a>
          </div>
          <p className="mt-4 text-[0.8125rem] text-[var(--color-faint)]">
            Already stocking our products? <Link href="/login" className="underline">Sign in</Link>{" "}
            to place an order and track it.
          </p>
        </div>

        <div className="overflow-hidden rounded-xl border border-[var(--color-line)]">
          <Image
            src="/dairy.jpeg"
            alt="Fresh dairy at the source"
            width={736}
            height={552}
            priority
            className="h-[320px] w-full object-cover"
          />
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-10">
        <h2 className="text-[0.6875rem] font-semibold uppercase tracking-wide text-[var(--color-faint)]">
          What we sell
        </h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {PRODUCTS.map((product) => (
            <div
              key={product.name}
              className="rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-5"
            >
              <p className="text-[0.9375rem] font-medium">{product.name}</p>
              <p className="mt-1 text-[0.8125rem] text-[var(--color-muted)]">{product.sizes}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-10">
        <div className="rounded-xl border border-[var(--color-line)] bg-[var(--color-accent-soft)] p-7">
          <h2 className="text-[1.0625rem] font-semibold tracking-[-0.01em]">
            Want to stock our products in your shop, gym or supermarket?
          </h2>
          <p className="mt-2 max-w-2xl text-[0.9375rem] leading-relaxed text-[var(--color-muted)]">
            Get in touch and we&apos;ll set you up with an account so you can order directly, see
            your order history, and track every delivery until it arrives.
          </p>
          <div className="mt-5 flex gap-3">
            <a href={`https://wa.me/${CONTACT_WHATSAPP}`} className={buttonClass}>
              WhatsApp us
            </a>
            <a href={`mailto:${CONTACT_EMAIL}`} className={secondaryButtonClass}>
              {CONTACT_EMAIL}
            </a>
          </div>
        </div>
      </section>
    </main>
  );
}
