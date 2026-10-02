"use client";

import { useState } from "react";
import { usePaged } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Card, Loading, More, Page, Table, Td, Tr } from "@/components/ui";

type Move = { id: number; drugName: string; delta: number; reason: string; note?: string; witnessId?: string; at: string };

export default function Movements() {
  const { facilityId } = useSession();
  const [controlled, setControlled] = useState(false);
  const list = usePaged<Move>(`/v1/pharmacy/stock/movements?facilityId=${facilityId}&controlledOnly=${controlled}`);
  return (
    <Page title="Stock ledger" sub="Every change in stock. It cannot be edited.">
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={controlled} onChange={(e) => setControlled(e.target.checked)} /> Controlled drugs only (the register)</label>
      <Card pad={false}>
        {list.loading && list.items.length === 0 ? <Loading /> : (
          <Table head={["When", "Product", "Change", "Reason", "Witness", "Note"]} empty="No movements.">
            {list.items.map((m) => <Tr key={m.id}><Td>{stamp(m.at)}</Td><Td>{m.drugName}</Td><Td className={m.delta < 0 ? "text-danger" : "text-good"}>{m.delta > 0 ? "+" : ""}{m.delta}</Td><Td>{m.reason}</Td><Td>{m.witnessId?.slice(0, 8)}</Td><Td>{m.note}</Td></Tr>)}
          </Table>
        )}
        <More onMore={list.more} loading={list.loading} />
      </Card>
    </Page>
  );
}
