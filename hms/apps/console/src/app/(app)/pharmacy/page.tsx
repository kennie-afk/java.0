"use client";

import { useState } from "react";
import { usePaged } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Input, Loading, More, Page, Status, Table, Tabs, Td, Tr } from "@/components/ui";

type Pending = { orderId: string; patientName: string; drugName: string; dose?: string; frequency?: string; quantity: number; dispensed: number; status: string; priority: string; orderedAt: string };
type Stock = { drugId: string; genericName: string; strength?: string; form: string; controlled: boolean; usable: number; expired: number; reorderLevel: number; belowReorder: boolean };

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
      <Tabs value={tab} onChange={setTab} tabs={[{ key: "queue", label: "Prescriptions" }, { key: "stock", label: "Stock" }]} />
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
