"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, Confirm, ErrorNote, Input, Loading, Page, Status, Table, Td, Tr, useAction } from "@/components/ui";

type Item = { id: string; testName: string; resultType: string; unit?: string; refLow?: number; refHigh?: number; status: string; specimenBarcode?: string; resultNumeric?: number; resultText?: string; flag?: string; critical: boolean; criticalAckAt?: string; resultHidden: boolean };
type Order = { id: string; orderNumber: string; patientId: string; patientName: string; priority: string; status: string; clinicalInfo?: string; createdAt: string; items: Item[] };

export default function LabOrder({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const o = useFetch<Order>(`/v1/lab/orders/${id}`);
  const [vals, setVals] = useState<Record<string, string>>({});
  const act = useAction();
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await o.reload(); });
  if (o.loading || !o.data) return <Page title="Lab order">{o.error ? <ErrorNote error={o.error} /> : <Loading />}</Page>;
  const d = o.data;
  return (
    <Page title={d.orderNumber} sub={`${d.patientName} · ordered ${stamp(d.createdAt)}${d.clinicalInfo ? " · " + d.clinicalInfo : ""}`}
      actions={<>
        <Status value={d.status} />
        {can("lab:enter") && d.items.some((i) => i.status === "PENDING") && <Button busy={act.busy} onClick={() => void go(() => post(`/v1/lab/orders/${id}/collect`))}>Mark specimens collected</Button>}
        {d.status === "ORDERED" && (can("orders:write") || can("lab:enter")) && <Confirm label="Cancel order" prompt="Cancel this order?" needsReason minReason={3} onConfirm={(reason) => go(() => post(`/v1/lab/orders/${id}/cancel`, { reason }))} />}
      </>}>
      <ErrorNote error={act.error} />
      <Card pad={false}>
        <Table head={["Test", "Specimen", "Result", "Range", "Status", ""]} empty="No tests.">
          {d.items.map((i) => (
            <Tr key={i.id}>
              <Td>{i.testName}</Td><Td>{i.specimenBarcode}</Td>
              <Td>
                {i.resultHidden ? <span className="text-muted">Awaiting validation</span> : <>{i.resultNumeric ?? i.resultText} {i.unit} {i.flag && <Badge tone={i.critical ? "danger" : i.flag === "N" ? "good" : "warn"}>{i.flag}</Badge>}</>}
                {i.status === "COLLECTED" && can("lab:enter") && (
                  <span className="flex gap-1"><Input className="max-w-28" placeholder="Value" value={vals[i.id] ?? ""} onChange={(e) => setVals({ ...vals, [i.id]: e.target.value })} />
                    <Button busy={act.busy} onClick={() => void go(() => post(`/v1/lab/items/${i.id}/result`, i.resultType === "NUMERIC" ? { numeric: Number(vals[i.id]) } : { text: vals[i.id] }))}>Enter</Button></span>
                )}
              </Td>
              <Td>{i.refLow ?? ""}{i.refLow !== undefined || i.refHigh !== undefined ? " - " : ""}{i.refHigh ?? ""}</Td>
              <Td><Status value={i.status} /></Td>
              <Td>
                {i.status === "RESULTED" && can("lab:validate") && <Button busy={act.busy} onClick={() => void go(() => post(`/v1/lab/items/${i.id}/validate`))}>Validate</Button>}
                {i.critical && !i.criticalAckAt && !i.resultHidden && <Confirm label="Acknowledge" variant="secondary" prompt="Record who was told and what was done." needsReason minReason={5} onConfirm={(note) => go(() => post(`/v1/lab/items/${i.id}/acknowledge`, { note }))} />}
              </Td>
            </Tr>
          ))}
        </Table>
      </Card>
      <p className="text-sm text-muted">Whoever validates a result must be a different person from whoever entered it. Clinicians see only validated results.</p>
    </Page>
  );
}
