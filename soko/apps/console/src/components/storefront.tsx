"use client";

import { newKey } from "@/lib/idempotency";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useActionState, useEffect, useMemo, useRef, useState } from "react";
import { checkout, type CheckoutState } from "@/app/shop/actions";
import { Badge, Notice, buttonClass, inputClass, secondaryButtonClass } from "@/components/ui";
import { ksh } from "@/lib/money";
import type { ShopProduct } from "@/app/shop/page";

const INITIAL: CheckoutState = { error: null, reference: null, orderId: null };

const href = (q: string, page: number) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (page > 0) params.set("page", String(page + 1));
  const text = params.toString();
  return text ? `?${text}` : "?";
};

export function Storefront({
  products,
  q,
  page,
  pageSize,
  matches,
  hasMore
}: {
  products: ShopProduct[];
  q: string;
  page: number;
  pageSize: number;
  matches: number;
  hasMore: boolean;
}) {
  const router = useRouter();
  const [basket, setBasket] = useState<Record<string, number>>({});
  const [search, setSearch] = useState(q);
  const [state, action, placing] = useActionState(checkout, INITIAL);
  // The basket outlives the page of products on screen: searching or paging must not lose lines.
  const known = useRef<Record<string, ShopProduct>>({});
  products.forEach((p) => { known.current[p.id] = p; });

  // A placed order empties the basket, so the same basket cannot be sent twice by accident.
  const placedId = state.orderId;
  useEffect(() => {
    if (placedId) setBasket({});
  }, [placedId]);

  const lines = useMemo(
    () =>
      Object.entries(basket)
        .filter(([, qty]) => qty > 0)
        .map(([productId, quantity]) => ({ productId, quantity })),
    [basket]
  );

  const total = useMemo(
    () =>
      lines.reduce((sum, line) => {
        const product = known.current[line.productId];
        return sum + (product ? product.priceCents * line.quantity : 0);
      }, 0),
    [lines]
  );

  // One key per distinct basket; a resubmit of the same basket returns the first order.
  const basketSignature = JSON.stringify(lines);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const idempotencyKey = useMemo(() => newKey(), [basketSignature, placedId]);

  const change = (id: string, delta: number, max: number) =>
    setBasket((current) => {
      const next = Math.min(Math.max((current[id] ?? 0) + delta, 0), max);
      return { ...current, [id]: next };
    });

  const categories = [...new Set(products.map((p) => p.category))];

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
      <div className="space-y-7">
        <form role="search" className="flex max-w-md items-center gap-2"
          onSubmit={(event) => { event.preventDefault(); router.push(href(search.trim(), 0)); }}>
          <input value={search} onChange={(event) => setSearch(event.target.value)} maxLength={80}
            placeholder="Search products" aria-label="Search products" className={`${inputClass} !mt-0`} />
          <button type="submit" className={buttonClass}>Search</button>
          {q ? <Link href="?" onClick={() => setSearch("")} className={secondaryButtonClass}>Clear</Link> : null}
        </form>
        {matches === 0 ? (
          <p className="text-[0.958rem] text-[var(--color-muted)]">
            {q ? `Nothing in stock matches "${q}".` : "Nothing is in stock right now."}
          </p>
        ) : null}
        {categories.map((category) => (
          <section key={category}>
            <h2 className="mb-3 text-[0.875rem] font-medium uppercase tracking-wide text-[var(--color-faint)]">
              {category}
            </h2>
            <div className="grid gap-3 sm:grid-cols-2">
              {products
                .filter((p) => p.category === category)
                .map((product) => {
                  const qty = basket[product.id] ?? 0;
                  return (
                    <div key={product.id}
                      className="rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-4">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="text-[1.083rem] font-medium">{product.name}</p>
                          <p className="mt-0.5 text-[0.875rem] text-[var(--color-muted)]">
                            per {product.unit} · {product.inStock} available
                          </p>
                        </div>
                        {product.chilled ? <Badge value="Chilled" /> : null}
                      </div>

                      <div className="mt-3 flex items-center justify-between">
                        <span className="text-[1.25rem] font-semibold tabular-nums">
                          {ksh(product.priceCents)}
                        </span>
                        {qty === 0 ? (
                          <button type="button" onClick={() => change(product.id, 1, product.inStock)}
                            className={secondaryButtonClass}>
                            Add
                          </button>
                        ) : (
                          <span className="flex items-center gap-2">
                            <button type="button" onClick={() => change(product.id, -1, product.inStock)}
                              className="h-7 w-7 rounded-full border border-[var(--color-line)] text-[1rem] leading-none hover:bg-[var(--color-raised)]">
                              −
                            </button>
                            <span className="w-6 text-center text-[1rem] font-medium tabular-nums">{qty}</span>
                            <button type="button" onClick={() => change(product.id, 1, product.inStock)}
                              className="h-7 w-7 rounded-full border border-[var(--color-line)] text-[1rem] leading-none hover:bg-[var(--color-raised)]">
                              +
                            </button>
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}
            </div>
          </section>
        ))}
        {matches > 0 ? (
          <div className="flex items-center justify-between text-[0.958rem] text-[var(--color-muted)]">
            <span className="tabular-nums">
              {page * pageSize + 1}-{page * pageSize + products.length} of {matches}
            </span>
            <span className="flex gap-2">
              {page > 0 ? <Link href={href(q, page - 1)} className={secondaryButtonClass}>Previous</Link> : null}
              {hasMore ? <Link href={href(q, page + 1)} className={secondaryButtonClass}>Next</Link> : null}
            </span>
          </div>
        ) : null}
      </div>

      <aside className="lg:sticky lg:top-24 lg:self-start">
        <div className="rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-5">
          <h2 className="text-[1.083rem] font-semibold">Your basket</h2>

          {state.reference ? (
            <div className="mt-3">
              <Notice tone="good">
                Order {state.reference} placed.{" "}
                <Link href={`/shop/orders/${state.orderId}`} className="font-medium underline">Pay with M-Pesa</Link>
              </Notice>
            </div>
          ) : null}
          {state.error ? (
            <div className="mt-3"><Notice tone="danger">{state.error}</Notice></div>
          ) : null}

          {lines.length === 0 ? (
            <p className="mt-3 text-[0.958rem] text-[var(--color-muted)]">Nothing in it yet.</p>
          ) : (
            <>
              <ul className="mt-3 space-y-2 text-[0.958rem]">
                {lines.map((line) => {
                  const product = known.current[line.productId];
                  if (!product) return null;
                  return (
                    <li key={line.productId} className="flex justify-between gap-2">
                      <span className="text-[var(--color-muted)]">
                        {product.name} × {line.quantity}
                      </span>
                      <span className="tabular-nums">{ksh(product.priceCents * line.quantity)}</span>
                    </li>
                  );
                })}
              </ul>
              <div className="mt-4 flex justify-between border-t border-[var(--color-line)] pt-3">
                <span className="text-[1rem] font-medium">Total</span>
                <span className="text-[1rem] font-semibold tabular-nums">{ksh(total)}</span>
              </div>
              <form action={action} className="mt-4">
                <input type="hidden" name="basket" value={basketSignature} />
                <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
                <button type="submit" className={`${buttonClass} w-full justify-center`} disabled={placing}>
                  {placing ? "Placing…" : "Place order"}
                </button>
              </form>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
