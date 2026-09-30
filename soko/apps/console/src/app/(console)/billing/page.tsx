import { api, describeError, ksh } from "@/lib/api";
import { ActionForm } from "@/components/action-form";
import { Badge, Card, Field, Notice, PageHeader, Stat, Table, inputClass, rowClass, secondaryButtonClass, selectClass } from "@/components/ui";
import { changePlan, generateInvoice, payInvoice } from "../actions";

interface Subscription { plan: string; monthlyFeeCents: number; commissionBps: number; startedAt: string }
interface Invoice {
  id: string; reference: string; periodStart: string; periodEnd: string; subscriptionFeeCents: number;
  commissionCents: number; totalCents: number; status: string; dueAt: string; paidAt: string | null;
}
interface LedgerRow { type: string; referenceType: string; amountCents: number; description: string | null; createdAt: string }

const PLANS = [
  { id: "FREE", label: "Free", fee: "KSh 0 a month", take: "5.00% of each order", who: "No upfront cost while the first orders come in." },
  { id: "GROWTH", label: "Growth", fee: "KSh 2,999 a month", take: "3.50% of each order", who: "A distributor with regular weekly volume." },
  { id: "SCALE", label: "Scale", fee: "KSh 9,999 a month", take: "2.00% of each order", who: "High volume: the falling commission is the reason to stay." }
];

const day = (iso: string) => new Date(iso).toLocaleDateString("en-KE", { day: "numeric", month: "short", year: "numeric" });
const today = () => new Date().toISOString().slice(0, 10);
const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString().slice(0, 10);

export default async function BillingPage({ searchParams }: { searchParams: Promise<{ notice?: string }> }) {
  const { notice } = await searchParams;
  let subscription: Subscription | null = null;
  let invoices: Invoice[] = [];
  let ledger: LedgerRow[] = [];
  let error: string | null = null;
  try {
    [subscription, invoices, ledger] = await Promise.all([
      api.get<Subscription>("/v1/billing/subscription"),
      api.get<Invoice[]>("/v1/billing/invoices?limit=24"),
      api.get<LedgerRow[]>("/v1/billing/ledger?limit=25")
    ]);
  } catch (caught) {
    error = describeError(caught);
  }
  if (error || !subscription) {
    return (<><PageHeader title="Billing" /><Notice tone="danger">{error}</Notice></>);
  }

  const open = invoices.filter((i) => i.status === "ISSUED");
  const owed = open.reduce((sum, i) => sum + i.totalCents, 0);
  const paid = invoices.filter((i) => i.status === "PAID").reduce((sum, i) => sum + i.totalCents, 0);

  return (
    <>
      <PageHeader title="Billing" subtitle="What Soko charges you: a monthly fee plus a commission on each order. Your own margin is separate." />

      {notice?.startsWith("plan-") ? (
        <div className="mb-4"><Notice tone="good">Plan changed to {notice.slice(5)}. It applies to orders placed from now on; invoices already issued keep their terms.</Notice></div>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Plan" value={subscription.plan} hint={`since ${day(subscription.startedAt)}`} tone="accent" />
        <Stat label="Monthly fee" value={ksh(subscription.monthlyFeeCents)} hint="charged on each invoice" />
        <Stat label="Commission" value={`${(subscription.commissionBps / 100).toFixed(2)}%`} hint="of each order's revenue" />
        <Stat label="Outstanding" value={ksh(owed)} hint={`${open.length} open · ${ksh(paid)} paid to date`} tone={owed ? "warn" : "good"} />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        {PLANS.map((plan) => {
          const current = plan.id === subscription.plan;
          return (
            <Card key={plan.id} title={plan.label} description={plan.who}
              actions={current ? <Badge value="Active" /> : undefined}>
              <p className="text-[1.083rem] font-semibold tabular-nums">{plan.fee}</p>
              <p className="mt-0.5 text-[0.875rem] text-[var(--color-muted)]">{plan.take}</p>
              {!current ? (
                <ActionForm action={changePlan} submit={`Switch to ${plan.label}`} pending="Switching…"
                  button={secondaryButtonClass} className="mt-3">
                  <input type="hidden" name="plan" value={plan.id} />
                </ActionForm>
              ) : null}
            </Card>
          );
        })}
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-[2fr_1fr]">
        <Card title="Invoices" description="Commission accrues per order and is billed in a period you choose.">
          {invoices.length === 0 ? (
            <p className="text-[0.875rem] text-[var(--color-muted)]">Nothing billed yet.</p>
          ) : (
            <Table head={["Invoice", "Period", "Fee", "Commission", "Total", "Status", ""]}>
              {invoices.map((invoice) => (
                <tr key={invoice.id} className={rowClass}>
                  <td className="px-3.5 py-2.5 font-medium">{invoice.reference}</td>
                  <td className="px-3.5 py-2.5 text-[var(--color-muted)]">{day(invoice.periodStart)} – {day(invoice.periodEnd)}</td>
                  <td className="px-3.5 py-2.5 tabular-nums">{ksh(invoice.subscriptionFeeCents)}</td>
                  <td className="px-3.5 py-2.5 tabular-nums">{ksh(invoice.commissionCents)}</td>
                  <td className="px-3.5 py-2.5 font-medium tabular-nums">{ksh(invoice.totalCents)}</td>
                  <td className="px-3.5 py-2.5">
                    <Badge value={invoice.status === "ISSUED" ? "Open" : invoice.status} />
                    {invoice.paidAt ? <span className="ml-2 text-[0.833rem] text-[var(--color-faint)]">{day(invoice.paidAt)}</span> : null}
                  </td>
                  <td className="px-3.5 py-2.5">
                    {invoice.status === "ISSUED" ? (
                      <ActionForm action={payInvoice} submit="Pay with M-Pesa" pending="Sending…" className="flex items-center gap-2"
                        button={secondaryButtonClass}>
                        <input type="hidden" name="invoiceId" value={invoice.id} />
                        <input name="msisdn" aria-label="M-Pesa number" placeholder="0712 345 678" className={`${inputClass} mt-0 w-32`} />
                      </ActionForm>
                    ) : null}
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>

        <Card title="Issue an invoice" description="Bills every unbilled commission in the period, plus the monthly fee.">
          <ActionForm action={generateInvoice} submit="Issue invoice" pending="Issuing…">
            <Field label="From"><input type="date" name="from" defaultValue={daysAgo(28)} className={inputClass} /></Field>
            <Field label="To"><input type="date" name="to" defaultValue={today()} className={inputClass} /></Field>
          </ActionForm>
        </Card>
      </div>

      <div className="mt-6">
        <Card title="Ledger" description="Append-only. A correction is a new offsetting entry, never an edit, so the entries for an invoice sum exactly to its total.">
          <Table head={["When", "Entry", "Against", "Amount", "Note"]}>
            {ledger.map((row, index) => (
              <tr key={index} className={rowClass}>
                <td className="px-3.5 py-2 text-[var(--color-muted)]">{day(row.createdAt)}</td>
                <td className="px-3.5 py-2">{row.type.replaceAll("_", " ").toLowerCase()}</td>
                <td className="px-3.5 py-2 text-[var(--color-muted)]">{row.referenceType.toLowerCase()}</td>
                <td className={`px-3.5 py-2 tabular-nums ${row.amountCents < 0 ? "text-[var(--color-danger)]" : ""}`}>{ksh(row.amountCents)}</td>
                <td className="px-3.5 py-2 text-[var(--color-muted)]">{row.description}</td>
              </tr>
            ))}
          </Table>
        </Card>
      </div>
    </>
  );
}
