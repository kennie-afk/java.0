"use client";

import { use, useState } from "react";
import { post, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { useSession } from "@/lib/session";
import { ImagingImages } from "@/components/ImagingImages";
import { Badge, Button, Card, Confirm, ErrorNote, Field, Grid, Input, KV, Loading, Page, Status, Textarea, useAction } from "@/components/ui";

type Order = {
  id: string; orderNumber: string; patientName: string; priority: string; status: string; procedureName: string; modality: string; bodyRegion?: string; clinicalInfo?: string;
  createdAt: string; performedAt?: string; techniqueNote?: string; findings?: string; impression?: string; critical: boolean; criticalNote?: string; criticalAckAt?: string;
  criticalAckNote?: string; reportedAt?: string; signedAt?: string; reportHidden: boolean; releasedAt?: string;
};

export default function ImagingOrder({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const o = useFetch<Order>(`/v1/imaging/orders/${id}`);
  const [tech, setTech] = useState("");
  const [rep, setRep] = useState({ findings: "", impression: "", critical: false, criticalNote: "" });
  const act = useAction();
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await o.reload(); });
  if (o.loading || !o.data) return <Page title="Imaging order">{o.error ? <ErrorNote error={o.error} /> : <Loading />}</Page>;
  const d = o.data;
  const editing = d.status === "PERFORMED";
  return (
    <Page title={d.orderNumber} sub={`${d.patientName} · ${d.procedureName} (${d.modality}) · ordered ${stamp(d.createdAt)}${d.clinicalInfo ? " · " + d.clinicalInfo : ""}`}
      actions={<>
        <Status value={d.status} />
        {d.status === "ORDERED" && (can("orders:write") || can("imaging:perform")) && <Confirm label="Cancel order" prompt="Cancel this order?" needsReason minReason={3} onConfirm={(reason) => go(() => post(`/v1/imaging/orders/${id}/cancel`, { reason }))} />}
        {d.status === "SIGNED" && can("portal:release") && <Button variant="secondary" busy={act.busy} onClick={() => void go(() => post(`/v1/portal/imaging-orders/${id}/${d.releasedAt ? "withdraw" : "release"}`))}>{d.releasedAt ? "Withdraw from portal" : "Release to patient"}</Button>}
        {d.status === "REPORTED" && can("imaging:sign") && <Button busy={act.busy} onClick={() => void go(() => post(`/v1/imaging/orders/${id}/sign`))}>Sign report</Button>}
      </>}>
      <ErrorNote error={act.error} />
      {d.status === "ORDERED" && can("imaging:perform") && (
        <Card title="Study">
          <div className="flex gap-2"><Input placeholder="Technique note (optional)" value={tech} onChange={(e) => setTech(e.target.value)} /><Button busy={act.busy} onClick={() => void go(() => post(`/v1/imaging/orders/${id}/perform`, { techniqueNote: tech || undefined }))}>Mark performed</Button></div>
        </Card>
      )}
      {(d.performedAt || d.techniqueNote) && <Card title="Study"><Grid cols={2}><KV k="Performed" v={d.performedAt ? stamp(d.performedAt) : "-"} /><KV k="Technique" v={d.techniqueNote ?? "-"} /></Grid></Card>}
      {d.status !== "ORDERED" && d.status !== "CANCELLED" && <ImagingImages orderId={id} status={d.status} canAttach={can("imaging:perform")} />}
      {editing && can("imaging:perform") && (
        <Card title="Report">
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void go(() => post(`/v1/imaging/orders/${id}/report`, { findings: rep.findings || undefined, impression: rep.impression, critical: rep.critical, criticalNote: rep.critical ? rep.criticalNote : undefined })); }}>
            <Field label="Findings"><Textarea rows={5} value={rep.findings} onChange={(e) => setRep({ ...rep, findings: e.target.value })} /></Field>
            <Field label="Impression"><Textarea rows={2} required minLength={3} value={rep.impression} onChange={(e) => setRep({ ...rep, impression: e.target.value })} /></Field>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={rep.critical} onChange={(e) => setRep({ ...rep, critical: e.target.checked })} /> Critical finding that needs action now</label>
            {rep.critical && <Field label="What is critical"><Input required value={rep.criticalNote} onChange={(e) => setRep({ ...rep, criticalNote: e.target.value })} /></Field>}
            <Button type="submit" busy={act.busy}>Submit report for signing</Button>
          </form>
        </Card>
      )}
      {d.reportHidden ? <Card title="Report"><p className="text-sm text-muted">Awaiting signature. A report is shown to clinicians only once a second person has signed it.</p></Card> : d.impression && (
        <Card title="Report" actions={d.critical ? <Badge tone="danger">Critical</Badge> : undefined}>
          {d.findings && <p className="whitespace-pre-wrap text-sm">{d.findings}</p>}
          <p className="pt-2 text-sm font-semibold">Impression: <span className="font-normal">{d.impression}</span></p>
          {d.critical && <p className="pt-2 text-sm text-danger">Critical: {d.criticalNote}{d.criticalAckAt ? ` · acknowledged ${stamp(d.criticalAckAt)}: ${d.criticalAckNote}` : ""}</p>}
          {d.critical && !d.criticalAckAt && (can("orders:write") || can("imaging:sign")) && <div className="pt-2"><Confirm label="Acknowledge" variant="secondary" prompt="Record who was told and what was done." needsReason minReason={5} onConfirm={(note) => go(() => post(`/v1/imaging/orders/${id}/acknowledge`, { note }))} /></div>}
          {d.signedAt && <p className="pt-2 text-xs text-muted">Signed {stamp(d.signedAt)}</p>}
        </Card>
      )}
      <p className="text-sm text-muted">Whoever signs a report must be a different person from whoever wrote it. This record holds the report, not the images.</p>
    </Page>
  );
}
