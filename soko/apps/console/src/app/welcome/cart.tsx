"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { requestOtp, verifyOtpAndOrder, type OrderResult } from "@/app/welcome/actions";
import type { PublicProduct } from "@/app/welcome/storefront";

const STORAGE_KEY = "freshferm-cart-v1";

export interface CartLine {
  product: PublicProduct;
  quantity: number;
}

interface CartContextValue {
  lines: CartLine[];
  count: number;
  subtotalCents: number;
  add: (product: PublicProduct) => void;
  setQuantity: (productId: string, quantity: number) => void;
  remove: (productId: string) => void;
  clear: () => void;
  isOpen: boolean;
  open: () => void;
  close: () => void;
  lastAdded: string | null;
}

const CartContext = createContext<CartContextValue | null>(null);

function readStoredLines(): CartLine[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as CartLine[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>([]);
  const [hydrated, setHydrated] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [lastAdded, setLastAdded] = useState<string | null>(null);

  useEffect(() => {
    setLines(readStoredLines());
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
    } catch {
      // Private browsing or blocked storage: the cart still works for this
      // page view, it just won't survive a refresh. Not worth failing over.
    }
  }, [lines, hydrated]);

  useEffect(() => {
    if (!lastAdded) return;
    const timer = window.setTimeout(() => setLastAdded(null), 1800);
    return () => window.clearTimeout(timer);
  }, [lastAdded]);

  const value = useMemo<CartContextValue>(() => {
    const count = lines.reduce((total, line) => total + line.quantity, 0);
    const subtotalCents = lines.reduce(
      (total, line) => total + line.product.priceCents * line.quantity,
      0
    );

    return {
      lines,
      count,
      subtotalCents,
      add: (product) => {
        setLines((current) => {
          const existing = current.find((line) => line.product.id === product.id);
          if (existing) {
            return current.map((line) =>
              line.product.id === product.id
                ? { ...line, quantity: Math.min(line.quantity + 1, product.inStock) }
                : line
            );
          }
          return [...current, { product, quantity: 1 }];
        });
        setLastAdded(product.id);
      },
      setQuantity: (productId, quantity) => {
        setLines((current) =>
          quantity <= 0
            ? current.filter((line) => line.product.id !== productId)
            : current.map((line) =>
                line.product.id === productId
                  ? { ...line, quantity: Math.min(quantity, line.product.inStock) }
                  : line
              )
        );
      },
      remove: (productId) => {
        setLines((current) => current.filter((line) => line.product.id !== productId));
      },
      clear: () => setLines([]),
      isOpen,
      open: () => setIsOpen(true),
      close: () => setIsOpen(false),
      lastAdded,
    };
  }, [lines, isOpen, lastAdded]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error("useCart must be used inside a CartProvider");
  }
  return context;
}

function ksh(cents: number): string {
  return `KSh ${(cents / 100).toLocaleString("en-KE", { maximumFractionDigits: 0 })}`;
}

export function CartButton() {
  const { count, open, lastAdded } = useCart();
  return (
    <button
      onClick={open}
      aria-label="Open cart"
      className={`relative flex items-center gap-2 rounded-sm border border-[var(--color-line)] bg-white px-3 py-1.5 text-[0.958rem] transition-colors hover:border-[var(--color-accent)] ${
        lastAdded ? "ring-2 ring-[var(--color-accent)]/30" : ""
      }`}
    >
      <CartIcon className="h-3.5 w-3.5" />
      Cart
      {count > 0 && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-sm bg-[var(--color-ink)] px-1 text-[0.833rem] text-white tabular-nums">
          {count}
        </span>
      )}
    </button>
  );
}

type Step = "cart" | "details" | "code" | "confirmed";

export function CartDrawer({ slug }: { slug?: string }) {
  const cart = useCart();
  const [step, setStep] = useState<Step>("cart");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [county, setCounty] = useState("");
  const [code, setCode] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [order, setOrder] = useState<OrderResult | null>(null);

  if (!cart.isOpen) {
    return null;
  }

  const closeAndReset = () => {
    cart.close();
    window.setTimeout(() => {
      setStep("cart");
      setError(null);
      setCode("");
    }, 200);
  };

  const sendCode = async () => {
    if (!slug) {
      setError("This storefront isn't connected to a live catalogue yet.");
      return;
    }
    setSubmitting(true);
    setError(null);
    const result = await requestOtp(slug, phone);
    setSubmitting(false);
    if (result.error) {
      setError(result.error);
      return;
    }
    setStep("code");
  };

  const submit = async () => {
    if (!slug) {
      setError("This storefront isn't connected to a live catalogue yet.");
      return;
    }
    const lines = cart.lines.map((line) => ({
      productId: line.product.id,
      quantity: line.quantity,
    }));

    setSubmitting(true);
    setError(null);

    const result = await verifyOtpAndOrder({ slug, phone, code, fullName, county, lines });

    setSubmitting(false);
    if (result.error || !result.order) {
      setError(result.error ?? "Something went wrong placing that order.");
      return;
    }
    setOrder(result.order);
    setStep("confirmed");
    cart.clear();
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <button
        aria-label="Close cart"
        onClick={closeAndReset}
        className="absolute inset-0 bg-black/30 backdrop-blur-[1px]"
      />
      <div className="relative flex h-full w-full max-w-md flex-col bg-white">
        <div className="flex items-center justify-between border-b border-[var(--color-line)] px-6 py-5">
          <p className="text-[1.208rem] font-medium">
            {step === "cart" && "Your cart"}
            {step === "details" && "Your details"}
            {step === "code" && "Enter the code"}
            {step === "confirmed" && "Order placed"}
          </p>
          <button
            onClick={closeAndReset}
            aria-label="Close"
            className="flex h-8 w-8 items-center justify-center rounded-sm text-[var(--color-faint)] transition-colors hover:bg-[var(--color-canvas)] hover:text-[var(--color-ink)]"
          >
            <CloseIcon className="h-4 w-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {step === "cart" && <CartView />}
          {step === "details" && (
            <DetailsForm
              fullName={fullName}
              phone={phone}
              county={county}
              onFullName={setFullName}
              onPhone={setPhone}
              onCounty={setCounty}
              error={error}
            />
          )}
          {step === "code" && <CodeForm code={code} onCode={setCode} phone={phone} error={error} />}
          {step === "confirmed" && order && <Confirmation order={order} />}
        </div>

        {step === "cart" && cart.lines.length > 0 && (
          <div className="border-t border-[var(--color-line)] px-6 py-5">
            <div className="flex items-center justify-between text-[1rem]">
              <span className="text-[var(--color-muted)]">Subtotal</span>
              <span className="font-medium tabular-nums">{ksh(cart.subtotalCents)}</span>
            </div>
            <button
              onClick={() => setStep("details")}
              className="mt-4 w-full rounded-sm bg-[var(--color-ink)] py-2 text-[0.958rem] text-white transition-colors"
            >
              Checkout
            </button>
          </div>
        )}

        {step === "details" && (
          <div className="border-t border-[var(--color-line)] px-6 py-5">
            <button
              onClick={sendCode}
              disabled={submitting}
              className="w-full rounded-sm bg-[var(--color-ink)] py-2 text-[0.958rem] text-white transition-colors disabled:opacity-60"
            >
              {submitting ? "Sending code…" : "Send code"}
            </button>
            <button
              onClick={() => setStep("cart")}
              className="mt-2 w-full py-1.5 text-[0.958rem] text-[var(--color-muted)] transition-colors hover:text-[var(--color-ink)]"
            >
              Back to cart
            </button>
          </div>
        )}

        {step === "code" && (
          <div className="border-t border-[var(--color-line)] px-6 py-5">
            <div className="flex items-center justify-between text-[1rem]">
              <span className="text-[var(--color-muted)]">Total</span>
              <span className="font-medium tabular-nums">{ksh(cart.subtotalCents)}</span>
            </div>
            <button
              onClick={submit}
              disabled={submitting}
              className="mt-4 w-full rounded-sm bg-[var(--color-ink)] py-2 text-[0.958rem] text-white transition-colors disabled:opacity-60"
            >
              {submitting ? "Confirming…" : "Confirm & place order"}
            </button>
            <button
              onClick={() => setStep("details")}
              className="mt-2 w-full py-1.5 text-[0.958rem] text-[var(--color-muted)] transition-colors hover:text-[var(--color-ink)]"
            >
              Change number
            </button>
          </div>
        )}

        {step === "confirmed" && (
          <div className="border-t border-[var(--color-line)] px-6 py-5">
            <button
              onClick={closeAndReset}
              className="w-full rounded-sm border border-[var(--color-line)] py-2 text-[0.958rem] transition-colors hover:border-[var(--color-accent)]"
            >
              Continue shopping
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function CartView() {
  const cart = useCart();

  if (cart.lines.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center">
        <CartIcon className="h-8 w-8 text-[var(--color-faint)]" />
        <p className="text-[1rem] text-[var(--color-muted)]">Your cart is empty.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {cart.lines.map((line) => (
        <div key={line.product.id} className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-[1rem] font-medium">{line.product.name}</p>
            <p className="text-[0.875rem] text-[var(--color-faint)]">
              {ksh(line.product.priceCents)} · {line.product.unit}
            </p>
            <div className="mt-2 flex items-center gap-2">
              <button
                onClick={() => cart.setQuantity(line.product.id, line.quantity - 1)}
                aria-label="Decrease quantity"
                className="flex h-6 w-6 items-center justify-center rounded-sm border border-[var(--color-line)] text-[1rem] transition-colors hover:border-[var(--color-accent)]"
              >
                −
              </button>
              <span className="w-5 text-center text-[1rem] tabular-nums">{line.quantity}</span>
              <button
                onClick={() => cart.setQuantity(line.product.id, line.quantity + 1)}
                aria-label="Increase quantity"
                disabled={line.quantity >= line.product.inStock}
                className="flex h-6 w-6 items-center justify-center rounded-sm border border-[var(--color-line)] text-[1rem] transition-colors hover:border-[var(--color-accent)] disabled:opacity-40"
              >
                +
              </button>
            </div>
          </div>
          <div className="flex flex-col items-end gap-2">
            <p className="text-[1rem] font-medium tabular-nums">
              {ksh(line.product.priceCents * line.quantity)}
            </p>
            <button
              onClick={() => cart.remove(line.product.id)}
              className="text-[0.875rem] text-[var(--color-faint)] underline-offset-2 transition-colors hover:text-[var(--color-danger)] hover:underline"
            >
              Remove
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

function DetailsForm({
  fullName,
  phone,
  county,
  onFullName,
  onPhone,
  onCounty,
  error,
}: {
  fullName: string;
  phone: string;
  county: string;
  onFullName: (v: string) => void;
  onPhone: (v: string) => void;
  onCounty: (v: string) => void;
  error: string | null;
}) {
  const inputClass =
    "w-full rounded-sm border border-[var(--color-line)] bg-white px-3 py-2 text-[0.958rem] outline-none transition-colors focus:border-[var(--color-accent)]";
  const label = "mb-1.5 block text-[0.875rem] text-[var(--color-faint)]";

  return (
    <div className="flex flex-col gap-4">
      {error && (
        <div className="rounded-sm border border-[var(--color-danger)]/30 bg-[var(--color-danger-soft)] px-3.5 py-3 text-[0.958rem] text-[var(--color-danger)]">
          {error}
        </div>
      )}
      <p className="text-[0.958rem] text-[var(--color-muted)]">
        We&apos;ll text a code to this number to confirm it&apos;s you — no password to
        remember. If you&apos;ve ordered before, your name and county below are only used the
        first time.
      </p>
      <div>
        <label className={label}>Phone number</label>
        <input
          value={phone}
          onChange={(e) => onPhone(e.target.value)}
          className={inputClass}
          placeholder="+254 7XX XXX XXX"
        />
      </div>
      <div>
        <label className={label}>Your name</label>
        <input
          value={fullName}
          onChange={(e) => onFullName(e.target.value)}
          className={inputClass}
          placeholder="Jane Wanjiru"
        />
      </div>
      <div>
        <label className={label}>County</label>
        <input
          value={county}
          onChange={(e) => onCounty(e.target.value)}
          className={inputClass}
          placeholder="Nairobi"
        />
      </div>
    </div>
  );
}

function CodeForm({
  code,
  onCode,
  phone,
  error,
}: {
  code: string;
  onCode: (v: string) => void;
  phone: string;
  error: string | null;
}) {
  const inputClass =
    "w-full rounded-sm border border-[var(--color-line)] bg-white px-3 py-2 text-center text-[1.375rem] tracking-[0.3em] outline-none transition-colors focus:border-[var(--color-accent)]";

  return (
    <div className="flex flex-col gap-4">
      {error && (
        <div className="rounded-sm border border-[var(--color-danger)]/30 bg-[var(--color-danger-soft)] px-3.5 py-3 text-[0.958rem] text-[var(--color-danger)]">
          {error}
        </div>
      )}
      <p className="text-[0.958rem] text-[var(--color-muted)]">
        We sent a 6-digit code to <span className="text-[var(--color-ink)]">{phone}</span>.
      </p>
      <input
        value={code}
        onChange={(e) => onCode(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
        className={inputClass}
        placeholder="······"
        inputMode="numeric"
        autoFocus
      />
    </div>
  );
}

function Confirmation({ order }: { order: OrderResult }) {
  return (
    <div className="flex flex-col items-center gap-4 py-8 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-sm bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
        <CheckIcon className="h-6 w-6" />
      </div>
      <div>
        <p className="text-[1.208rem] font-medium">Thank you — order placed.</p>
        <p className="mt-1 text-[1rem] text-[var(--color-muted)]">
          Reference <span className="font-medium text-[var(--color-ink)]">{order.reference}</span>
        </p>
      </div>
      <div className="w-full rounded-sm border border-[var(--color-line)] p-4 text-left">
        {order.lines.map((line, index) => (
          <div key={index} className="flex justify-between py-1 text-[1rem]">
            <span className="text-[var(--color-muted)]">
              {line.quantity}× {line.product}
            </span>
            <span className="tabular-nums">{ksh(line.lineTotalCents)}</span>
          </div>
        ))}
        <div className="mt-2 flex justify-between border-t border-[var(--color-line)] pt-2 text-[1rem] font-medium">
          <span>Total</span>
          <span className="tabular-nums">{ksh(order.totalCents)}</span>
        </div>
      </div>
      <p className="text-[0.875rem] text-[var(--color-faint)]">
        We&apos;ll reach out on WhatsApp or by phone to confirm payment and delivery.
      </p>
    </div>
  );
}

function CartIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className={className} aria-hidden="true">
      <circle cx="9" cy="20" r="1.4" />
      <circle cx="18" cy="20" r="1.4" />
      <path d="M2 3h2l2.4 12.2a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 2-1.6L21 7H6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function CloseIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={className} aria-hidden="true">
      <path d="M6 6l12 12M18 6L6 18" strokeLinecap="round" />
    </svg>
  );
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={className} aria-hidden="true">
      <path d="M5 12l5 5 9-9" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
