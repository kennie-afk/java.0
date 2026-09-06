"use client";

import { useActionState, useMemo, useState } from "react";
import { checkout, type CheckoutState } from "@/app/shop/actions";
import { Badge, Notice, buttonClass, secondaryButtonClass } from "@/components/ui";
import { ksh } from "@/lib/money";
import type { ShopProduct } from "@/app/shop/page";

const INITIAL: CheckoutState = { error: null, reference: null };

export function Storefront({ products }: { products: ShopProduct[] }) {
  const [basket, setBasket] = useState<Record<string, number>>({});
  const [state, action, placing] = useActionState(checkout, INITIAL);

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
        const product = products.find((p) => p.id === line.productId);
        return sum + (product ? product.priceCents * line.quantity : 0);
      }, 0),
    [lines, products]
  );

  const change = (id: string, delta: number, max: number) =>
    setBasket((current) => {
      const next = Math.min(Math.max((current[id] ?? 0) + delta, 0), max);
      return { ...current, [id]: next };
    });

  const categories = [...new Set(products.map((p) => p.category))];

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
      <div className="space-y-7">
        {categories.map((category) => (
          <section key={category}>
            <h2 className="mb-3 text-[0.75rem] font-medium uppercase tracking-wide text-[var(--color-faint)]">
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
                          <p className="text-[0.9375rem] font-medium">{product.name}</p>
                          <p className="mt-0.5 text-[0.75rem] text-[var(--color-muted)]">
                            per {product.unit} · {product.inStock} available
                          </p>
                        </div>
                        {product.chilled ? <Badge value="Chilled" /> : null}
                      </div>

                      <div className="mt-3 flex items-center justify-between">
                        <span className="text-[1.0625rem] font-semibold tabular-nums">
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
                              className="h-7 w-7 rounded-full border border-[var(--color-line)] text-[0.875rem] leading-none hover:bg-[var(--color-raised)]">
                              −
                            </button>
                            <span className="w-6 text-center text-[0.875rem] font-medium tabular-nums">{qty}</span>
                            <button type="button" onClick={() => change(product.id, 1, product.inStock)}
                              className="h-7 w-7 rounded-full border border-[var(--color-line)] text-[0.875rem] leading-none hover:bg-[var(--color-raised)]">
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
      </div>

      <aside className="lg:sticky lg:top-24 lg:self-start">
        <div className="rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] p-5">
          <h2 className="text-[0.9375rem] font-semibold">Your basket</h2>

          {state.reference ? (
            <div className="mt-3">
              <Notice tone="good">Order {state.reference} placed.</Notice>
            </div>
          ) : null}
          {state.error ? (
            <div className="mt-3"><Notice tone="danger">{state.error}</Notice></div>
          ) : null}

          {lines.length === 0 ? (
            <p className="mt-3 text-[0.8125rem] text-[var(--color-muted)]">Nothing in it yet.</p>
          ) : (
            <>
              <ul className="mt-3 space-y-2 text-[0.8125rem]">
                {lines.map((line) => {
                  const product = products.find((p) => p.id === line.productId)!;
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
                <span className="text-[0.875rem] font-medium">Total</span>
                <span className="text-[0.875rem] font-semibold tabular-nums">{ksh(total)}</span>
              </div>
              <form action={action} className="mt-4">
                <input type="hidden" name="basket" value={JSON.stringify(lines)} />
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
