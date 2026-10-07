"use client";

import { useState } from "react";
import { post, useFetch, usePaged } from "@/lib/api";
import { date, stamp } from "@/lib/format";
import { ADJUSTMENT_REASONS, adjustmentProblem, type AdjustmentReason } from "@/lib/rules";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, ErrorNote, Input, Loading, More, Page, Select, Status, Table, Tabs, Td, Tr, useAction } from "@/components/ui";

type Pending = { orderId: string; patientName: string; drugName: string; dose?: string; frequency?: string; quantity: number; dispensed: number; status: string; priority: string; orderedAt: string };
type Stock = { drugId: string; genericName: string; strength?: string; form: string; controlled: boolean; usable: number; expired: number; reorderLevel: number; belowReorder: boolean };

type Batch = { id: string; drugName: string; batchNo: string; expiryDate: string; quantity: number; expired: boolean };

/** One stock correction, inline: a signed quantity, a reason and a note. The server checks it again and audits it. */
function Adjust({ batch, onDone }: { batch: Batch; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [delta, setDelta] = useState("");
  const [reason, setReason] = useState<AdjustmentReason>("ADJUSTMENT");
  const [note, setNote] = useState("");
  const { busy, error, run } = useAction();
  const problem = adjustmentProblem({ delta: Number(delta), reason, note, onHand: batch.quantity });
  if (!open) return <Button variant="secondary" onClick={() => setOpen(true)}>Adjust</Button>;
  return (
    <div className="space-y-2 rounded-lg border border-line bg-surface p-3">
      <div className="flex flex-wrap gap-2">
        <Input type="number" step="0.01" placeholder="Change (+/-)" aria-label="Change in quantity" value={delta} onChange={(e) => setDelta(e.target.value)} className="max-w-32" />
        <Select aria-label="Reason" value={reason} onChange={(e) => setReason(e.target.value as AdjustmentReason)} className="max-w-40">{ADJUSTMENT_REASONS.map((r) => <option key={r} value={r}>{r}</option>)}</Select>
      </div>
      <Input placeholder="Why (at least 5 characters)" aria-label="Note" value={note} onChange={(e) => setNote(e.target.value)} />
      {delta && problem && <div className="text-xs text-muted">{problem}</div>}
      <ErrorNote error={error} />
      <div className="flex gap-2">
        <Button busy={busy} disabled={!!problem} onClick={() => void run(async () => { await post("/v1/pharmacy/stock/adjustments", { batchId: batch.id, delta: Number(delta), reason, note: note.trim() }); setOpen(false); onDone(); })}>Save</Button>
        <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </div>
  );
}

function Batches({ facilityId, canStock }: { facilityId: string; canStock: boolean }) {
  const [within, setWithin] = useState("90");
  const b = useFetch<Batch[]>(`/v1/pharmacy/stock/batches?facilityId=${facilityId}${within ? `&expiringWithinDays=${within}` : ""}`);
  return (
    <Card pad={false}>
      <div className="flex items-center gap-2 border-b border-line p-3 text-sm">
        <span className="text-muted">Expiring within</span>
        <Select value={within} onChange={(e) => setWithin(e.target.value)} className="max-w-40" aria-label="Expiring within">
          <option value="30">30 days</option><option value="90">90 days</option><option value="180">180 days</option><option value="">Any (all batches)</option>
        </Select>
      </div>
      <ErrorNote error={b.error} />
      {b.loading && !b.data ? <Loading /> : (
        <Table head={["Product", "Batch", "Expiry", "On hand", ""]} empty="No batches match.">
          {(b.data ?? []).map((x) => (
            <Tr key={x.id} tone={x.expired ? "danger" : undefined}>
              <Td>{x.drugName}</Td><Td>{x.batchNo}</Td><Td>{date(x.expiryDate)} {x.expired && <Badge tone="danger">Expired</Badge>}</Td><Td>{x.quantity}</Td>
              <Td>{canStock && <Adjust batch={x} onDone={() => void b.reload()} />}</Td>
            </Tr>
          ))}
        </Table>
      )}
    </Card>
  );
}

export default function Pharmacy() {
  const { facilityId, can } = useSession();
  const [tab, setTab] = useState("queue");
  const [q, setQ] = useState("");
  const queue = usePaged<Pending>(tab === "queue" ? `/v1/pharmacy/queue?facilityId=${facilityId}` : null);
  const stock = usePaged<Stock>(tab === "stock" ? `/v1/pharmacy/stock?facilityId=${facilityId}${q ? `&q=${encodeURIComponent(q)}` : ""}` : null);
  return (
    <Page title="Pharmacy" actions={<>
      <Button variant="secondary" href="/pharmacy/movements">Stock ledger</Button>
      {can("pharmacy:stock") && <><Button variant="secondary" href="/pharmacy/drugs/new">Add product</Button><Button href="/pharmacy/receive">Receive stock</Button></>}
    </>}>
      <Tabs value={tab} onChange={setTab} tabs={[{ key: "queue", label: "Prescriptions" }, { key: "stock", label: "Stock" }, { key: "batches", label: "Batches and expiry" }]} />
      {tab === "queue" && (
        <Card pad={false}>
          {queue.loading && queue.items.length === 0 ? <Loading /> : (
            <Table head={["Ordered", "Patient", "Drug", "Qty", "Status", ""]} empty="No prescriptions waiting.">
              {queue.items.map((o) => (
                <Tr key={o.orderId}>
                  <Td>{stamp(o.orderedAt)}</Td><Td>{o.patientName}</Td><Td>{o.drugName} {o.dose} {o.frequency}</Td><Td>{o.dispensed}/{o.quantity}</Td><Td><Status value={o.status} /></Td>
                  <Td>{can("pharmacy:dispense") && <Button href={`/pharmacy/dispense/${o.orderId}?name=${encodeURIComponent(o.drugName)}&left=${o.quantity - o.dispensed}`}>Dispense</Button>}</Td>
                </Tr>
              ))}
            </Table>
          )}
          <More onMore={queue.more} loading={queue.loading} />
        </Card>
      )}
      {tab === "batches" && <Batches facilityId={facilityId} canStock={can("pharmacy:stock")} />}
      {tab === "stock" && (
        <Card pad={false}>
          <div className="border-b border-line p-3"><Input placeholder="Search products" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          <Table head={["Product", "Form", "Usable", "Expired", "Reorder at", ""]} empty="No products.">
            {stock.items.map((s) => (
              <Tr key={s.drugId}>
                <Td>{s.genericName} {s.strength} {s.controlled && <Badge tone="danger">Controlled</Badge>}</Td><Td>{s.form}</Td>
                <Td className={s.belowReorder ? "font-semibold text-warn" : ""}>{s.usable}</Td><Td className={s.expired > 0 ? "text-danger" : ""}>{s.expired}</Td><Td>{s.reorderLevel}</Td>
                <Td>{s.belowReorder && <Badge tone="warn">Reorder</Badge>}</Td>
              </Tr>
            ))}
          </Table>
          <More onMore={stock.more} loading={stock.loading} />
        </Card>
      )}
    </Page>
  );
}
