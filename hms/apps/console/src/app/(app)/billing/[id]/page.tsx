"use client";

import { use, useState } from "react";
import { useRouter } from "next/navigation";
import { api, post, useFetch } from "@/lib/api";
import { kes, stamp, uuid } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Confirm, ErrorNote, Field, Grid, Input, KV, Loading, Page, Select, Stat, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Line = { id: string; description: string; sourceType: string; quantity: number; unitPrice: number; lineTotal: number };
type Pay = { id: string; method: string; amount: number; status: string; receiptNumber?: string; mpesaCheckoutId?: string; mpesaReceipt?: string; completedAt?: string; reference?: string };
type Inv = { id: string; invoiceNumber: string; patientId: string; patientName: string; encounterId?: string; status: string; payerType: string; total: number; amountPaid: number; balance: number; lines: Line[]; payments: Pay[] };
type Charge = { id: string; name: string; price: number };

export default function Invoice({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const router = useRouter();
  const inv = useFetch<Inv>(`/v1/billing/invoices/${id}`);
  const charges = useFetch<Charge[]>(inv.data?.status === "DRAFT" ? "/v1/billing/charges" : null);
  const act = useAction();
  const [line, setLine] = useState({ chargeId: "", description: "", unitPrice: "", quantity: "1" });
  const [pay, setPay] = useState({ method: "CASH", amount: "", reference: "", phone: "" });
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await inv.reload(); });
  if (inv.loading || !inv.data) return <Page title="Invoice">{inv.error ? <ErrorNote error={inv.error} /> : <Loading />}</Page>;
  const d = inv.data;
  const draft = d.status === "DRAFT";
  const payable = d.status === "ISSUED" || d.status === "PARTIALLY_PAID";
  return (
    <Page title={d.invoiceNumber} sub={`${d.patientName} · payer ${d.payerType}`} actions={<>
      <Status value={d.status} />
      {draft && can("billing:post") && d.encounterId && <Button variant="secondary" busy={act.busy} onClick={() => void go(() => post(`/v1/billing/invoices/${id}/import-encounter`))}>Add dispensed items and lab tests</Button>}
      {draft && can("billing:post") && <Button busy={act.busy} onClick={() => void go(() => post(`/v1/billing/invoices/${id}/issue`))}>Issue</Button>}
      {can("claims:submit") && d.encounterId && d.payerType !== "CASH" && !draft && d.status !== "VOID" && <Button variant="secondary" onClick={() => void act.run(async () => { const c = await post<{ id: string }>("/v1/claims", { invoiceId: id }); router.push(`/claims/${c.id}`); })}>Prepare claim</Button>}
      {d.status !== "VOID" && can("billing:refund") && <Confirm label="Void" prompt="Void this invoice? Possible only when no payment stands." needsReason onConfirm={(reason) => go(() => post(`/v1/billing/invoices/${id}/void`, { reason }))} />}
    </>}>
      <ErrorNote error={act.error} />
      <Grid cols={3}><Stat icon="receipt" label="Total" value={kes(d.total)} /><Stat icon="money" label="Paid" value={kes(d.amountPaid)} /><Stat icon="clock" label="Balance" value={kes(d.balance)} tone={d.balance > 0 ? "warn" : undefined} /></Grid>
      <Card title="Lines" pad={false}>
        <Table head={["Description", "Source", "Qty", "Unit price", "Total", ""]} empty="No lines yet.">
          {d.lines.map((l) => (
            <Tr key={l.id}><Td>{l.description}</Td><Td>{l.sourceType}</Td><Td>{l.quantity}</Td><Td>{kes(l.unitPrice)}</Td><Td>{kes(l.lineTotal)}</Td>
              <Td>{draft && can("billing:post") && <Button variant="secondary" onClick={() => void go(() => api(`/v1/billing/invoices/${id}/lines/${l.id}`, { method: "DELETE" }))}>Remove</Button>}</Td></Tr>
          ))}
        </Table>
        {draft && can("billing:post") && (
          <form className="grid grid-cols-1 gap-2 border-t border-line p-4 sm:grid-cols-5" onSubmit={(e) => { e.preventDefault(); void go(async () => { await post(`/v1/billing/invoices/${id}/lines`, line.chargeId ? { chargeId: line.chargeId, quantity: Number(line.quantity) } : { description: line.description, unitPrice: Number(line.unitPrice), quantity: Number(line.quantity) }); setLine({ chargeId: "", description: "", unitPrice: "", quantity: "1" }); }); }}>
            <Select value={line.chargeId} onChange={(e) => setLine({ ...line, chargeId: e.target.value })}><option value="">Custom line</option>{(charges.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name} ({kes(c.price)})</option>)}</Select>
            <Input placeholder="Description" disabled={!!line.chargeId} value={line.description} onChange={(e) => setLine({ ...line, description: e.target.value })} />
            <Input type="number" step="0.01" placeholder="Unit price" disabled={!!line.chargeId} value={line.unitPrice} onChange={(e) => setLine({ ...line, unitPrice: e.target.value })} />
            <Input type="number" step="0.01" placeholder="Qty" value={line.quantity} onChange={(e) => setLine({ ...line, quantity: e.target.value })} />
            <Button type="submit" busy={act.busy}>Add line</Button>
          </form>
        )}
      </Card>
      <Card title="Payments" pad={false}>
        <Table head={["When", "Method", "Amount", "Status", "Receipt", ""]} empty="No payments.">
          {d.payments.map((p) => (
            <Tr key={p.id}><Td>{stamp(p.completedAt)}</Td><Td>{p.method} {p.mpesaReceipt}</Td><Td>{kes(p.amount)}</Td><Td><Status value={p.status} /></Td><Td>{p.receiptNumber}</Td>
              <Td>
                {p.status === "PENDING" && p.mpesaCheckoutId?.startsWith("MOCK-") && can("billing:post") && (
                  <span className="flex gap-1"><Badge tone="warn">Simulated</Badge>
                    <Button variant="secondary" onClick={() => void go(() => post("/v1/billing/mpesa/mock/complete", { checkoutRequestId: p.mpesaCheckoutId, success: true }))}>Customer paid</Button>
                    <Button variant="secondary" onClick={() => void go(() => post("/v1/billing/mpesa/mock/complete", { checkoutRequestId: p.mpesaCheckoutId, success: false }))}>Declined</Button></span>
                )}
                {p.status === "COMPLETED" && can("billing:refund") && <Confirm label="Reverse" prompt="Reverse this payment?" needsReason onConfirm={(reason) => go(() => post(`/v1/billing/payments/${p.id}/reverse`, { reason }))} />}
              </Td></Tr>
          ))}
        </Table>
        {payable && can("billing:post") && (
          <form className="grid grid-cols-1 gap-2 border-t border-line p-4 sm:grid-cols-5" onSubmit={(e) => { e.preventDefault(); void go(async () => {
            const key = uuid();
            if (pay.method === "MPESA") await post(`/v1/billing/invoices/${id}/mpesa`, { phone: pay.phone, amount: Number(pay.amount), idempotencyKey: key });
            else await post(`/v1/billing/invoices/${id}/payments`, { method: pay.method, amount: Number(pay.amount), reference: pay.reference || undefined, idempotencyKey: key });
            setPay({ ...pay, amount: "" });
          }); }}>
            <Select value={pay.method} onChange={(e) => setPay({ ...pay, method: e.target.value })}><option>CASH</option><option value="MPESA">M-PESA (simulated)</option><option>CARD</option><option>BANK</option></Select>
            <Input type="number" step="0.01" required placeholder={`Amount (max ${d.balance})`} value={pay.amount} onChange={(e) => setPay({ ...pay, amount: e.target.value })} />
            {pay.method === "MPESA" ? <Input required placeholder="Phone 07xx" value={pay.phone} onChange={(e) => setPay({ ...pay, phone: e.target.value })} /> : <Input placeholder="Reference" value={pay.reference} onChange={(e) => setPay({ ...pay, reference: e.target.value })} />}
            <Button type="submit" busy={act.busy}>{pay.method === "MPESA" ? "Send prompt" : "Record payment"}</Button>
          </form>
        )}
      </Card>
      {payable && <p className="text-sm text-muted">M-Pesa here is a simulation: no prompt is sent to any phone. A live integration has not been built or verified.</p>}
    </Page>
  );
}
