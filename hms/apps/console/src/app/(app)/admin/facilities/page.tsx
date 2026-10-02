"use client";

import { useState } from "react";
import { post, put, useFetch } from "@/lib/api";
import { Badge, Button, Card, ErrorNote, Field, Grid, Input, Loading, Page, Select, Table, Td, Tr, useAction } from "@/components/ui";

type Fac = { id: string; name: string; mflCode?: string; kephLevel?: number; ownership?: string; county?: string; active: boolean };

export default function Facilities() {
  const list = useFetch<Fac[]>("/v1/facilities");
  const [f, setF] = useState({ name: "", mflCode: "", kephLevel: "", ownership: "", county: "" });
  const act = useAction();
  return (
    <Page title="Facilities" sub="The MFL code is required for claims.">
      <ErrorNote error={act.error} />
      <Card pad={false}>
        {list.loading ? <Loading /> : (
          <Table head={["Name", "MFL code", "KEPH", "Ownership", "County", ""]} empty="None.">
            {(list.data ?? []).map((x) => (
              <Tr key={x.id}><Td>{x.name} {!x.active && <Badge tone="danger">Inactive</Badge>}</Td><Td>{x.mflCode}</Td><Td>{x.kephLevel}</Td><Td>{x.ownership}</Td><Td>{x.county}</Td>
                <Td><Button variant="secondary" onClick={() => void act.run(async () => { await put(`/v1/facilities/${x.id}`, { name: x.name, mflCode: x.mflCode, kephLevel: x.kephLevel, ownership: x.ownership, county: x.county, active: !x.active }); await list.reload(); })}>{x.active ? "Deactivate" : "Activate"}</Button></Td></Tr>
            ))}
          </Table>
        )}
      </Card>
      <Card title="Add a facility">
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); void act.run(async () => { await post("/v1/facilities", { name: f.name, mflCode: f.mflCode || undefined, kephLevel: f.kephLevel ? Number(f.kephLevel) : undefined, ownership: f.ownership || undefined, county: f.county || undefined }); setF({ name: "", mflCode: "", kephLevel: "", ownership: "", county: "" }); await list.reload(); }); }}>
          <Grid cols={4}>
            <Field label="Name"><Input required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
            <Field label="MFL code"><Input value={f.mflCode} onChange={(e) => setF({ ...f, mflCode: e.target.value })} /></Field>
            <Field label="KEPH level"><Select value={f.kephLevel} onChange={(e) => setF({ ...f, kephLevel: e.target.value })}><option value="">-</option>{[1, 2, 3, 4, 5, 6].map((n) => <option key={n}>{n}</option>)}</Select></Field>
            <Field label="Ownership"><Select value={f.ownership} onChange={(e) => setF({ ...f, ownership: e.target.value })}><option value="">-</option>{["PUBLIC", "PRIVATE", "FAITH_BASED", "NGO"].map((n) => <option key={n}>{n}</option>)}</Select></Field>
          </Grid>
          <Button type="submit" busy={act.busy}>Add</Button>
        </form>
      </Card>
    </Page>
  );
}
