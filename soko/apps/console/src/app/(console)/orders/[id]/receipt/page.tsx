import Link from "next/link";
import { api, describeError, ksh } from "@/lib/api";
import { Notice } from "@/components/ui";
import { Logo } from "@/components/logo";
import { PrintButton } from "@/components/print-button";
import { readSession } from "@/lib/session";

interface Line {
  product: string | null;
  supplier: string | null;
  quantity: number;
  unitPriceCents: number;
  unitCostCents: number;
  marginCents: number;
}

interface OrderDetail {
  id: string;
  reference: string;
  status: string;
  revenueCents: number;
  costCents: number;
  marginCents: number;
  lines: Line[];
}

export default async function ReceiptPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const session = await readSession();
  let order: OrderDetail | null = null;
  let error: string | null = null;

  try {
    order = await api.get<OrderDetail>(`/v1/orders/${id}`);
  } catch (caught) {
    error = describeError(caught);
  }

  if (error || !order) {
    return <Notice tone="danger">{error}</Notice>;
  }

  const issued = new Date().toLocaleString("en-KE", { dateStyle: "long", timeStyle: "short" });

  return (
    <div className="mx-auto max-w-[760px]">
      <div className="mb-5 flex items-center justify-between print:hidden">
        <Link href={`/orders/${order.id}`}
          className="text-[0.8125rem] text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline">
          Back to the order
        </Link>
        <PrintButton />
      </div>

      <article className="rounded-xl border border-[var(--color-line)] bg-white p-10 print:rounded-none print:border-0 print:p-0">
        <header className="flex items-start justify-between border-b border-[var(--color-line)] pb-6">
          <div className="flex items-center gap-3">
            <Logo className="h-9 w-9" />
            <div>
              <p className="text-[1.0625rem] font-semibold tracking-[-0.01em]">Soko</p>
              <p className="text-[0.75rem] text-[var(--color-muted)]">
                {session?.organisation ?? "Fresh produce distribution"}
              </p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-[0.6875rem] uppercase tracking-wide text-[var(--color-faint)]">Receipt</p>
            <p className="text-[1.0625rem] font-semibold tabular-nums">{order.reference}</p>
            <p className="text-[0.75rem] text-[var(--color-muted)]">{issued}</p>
          </div>
        </header>

        <table className="mt-7 w-full text-[0.8125rem]">
          <thead>
            <tr className="border-b border-[var(--color-line)] text-left">
              <th className="pb-2 font-medium text-[var(--color-muted)]">Item</th>
              <th className="pb-2 font-medium text-[var(--color-muted)]">Fulfilled by</th>
              <th className="pb-2 text-right font-medium text-[var(--color-muted)]">Qty</th>
              <th className="pb-2 text-right font-medium text-[var(--color-muted)]">Unit</th>
              <th className="pb-2 text-right font-medium text-[var(--color-muted)]">Amount</th>
            </tr>
          </thead>
          <tbody>
            {order.lines.map((line, index) => (
              <tr key={index} className="border-b border-[var(--color-line)] last:border-0">
                <td className="py-2.5 font-medium">{line.product}</td>
                <td className="py-2.5 text-[var(--color-muted)]">{line.supplier}</td>
                <td className="py-2.5 text-right tabular-nums">{line.quantity}</td>
                <td className="py-2.5 text-right tabular-nums">{ksh(line.unitPriceCents)}</td>
                <td className="py-2.5 text-right tabular-nums">
                  {ksh(line.unitPriceCents * line.quantity)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="mt-6 flex justify-end">
          <dl className="w-64 space-y-2 text-[0.8125rem]">
            <div className="flex justify-between">
              <dt className="text-[var(--color-muted)]">Subtotal</dt>
              <dd className="tabular-nums">{ksh(order.revenueCents)}</dd>
            </div>
            <div className="flex justify-between border-t border-[var(--color-line)] pt-2">
              <dt className="font-medium">Total due</dt>
              <dd className="font-semibold tabular-nums">{ksh(order.revenueCents)}</dd>
            </div>
          </dl>
        </div>

        <footer className="mt-9 border-t border-[var(--color-line)] pt-5 text-[0.6875rem] leading-relaxed text-[var(--color-faint)]">
          <p>
            Goods are shipped directly by the fulfilling supplier named against each line. Chilled
            items travel under an unbroken cold chain and are dispatched to arrive within their
            remaining shelf life.
          </p>
          <p className="mt-1.5">Reference {order.reference} · status {order.status.toLowerCase()}</p>
        </footer>
      </article>
    </div>
  );
}
