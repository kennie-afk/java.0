"use client";

import { use, useEffect, useState } from "react";
import { post, put, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, Confirm, ErrorNote, Field, Grid, Input, KV, Loading, Page, Status, useAction } from "@/components/ui";

type Staff = { id: string; email: string; fullName: string; cadre: string; licenceBody?: string; licenceNo?: string; phone?: string; status: string; mustChangePassword: boolean; roles: string[]; facilityIds: string[] };
type Role = { key: string; label: string };

export default function StaffPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { me, can } = useSession();
  const s = useFetch<Staff>(`/v1/staff/${id}`);
  const roles = useFetch<Role[]>("/v1/roles");
  const [picked, setPicked] = useState<string[]>([]);
  const [sites, setSites] = useState<string[]>([]);
  const [pw, setPw] = useState("");
  const act = useAction();
  useEffect(() => { if (s.data) { setPicked(s.data.roles); setSites(s.data.facilityIds); } }, [s.data]);
  const go = (fn: () => Promise<unknown>) => act.run(async () => { await fn(); await s.reload(); });
  if (s.loading || !s.data) return <Page title="Staff member">{s.error ? <ErrorNote error={s.error} /> : <Loading />}</Page>;
  const d = s.data;
  const toggle = (list: string[], v: string, on: boolean) => (on ? [...list, v] : list.filter((x) => x !== v));
  const manage = can("staff:manage");
  return (
    <Page title={d.fullName} sub={d.email} actions={<>
      <Status value={d.status} />
      {manage && d.status === "ACTIVE" && d.id !== me.practitionerId && <Confirm label="Disable account" prompt="Disable this account? They are signed out within seconds." onConfirm={() => go(() => post(`/v1/staff/${id}/disable`))} />}
      {manage && d.status === "DISABLED" && <Button onClick={() => void go(() => post(`/v1/staff/${id}/enable`))}>Enable account</Button>}
    </>}>
      <ErrorNote error={act.error} />
      <Grid cols={4}><KV k="Cadre" v={d.cadre} /><KV k="Licence" v={`${d.licenceBody ?? ""} ${d.licenceNo ?? ""}`} /><KV k="Phone" v={d.phone} /><KV k="Password" v={d.mustChangePassword ? "Temporary" : "Set by the person"} /></Grid>
      {manage && (
        <>
          <Card title="Roles and facilities" actions={<Button busy={act.busy} onClick={() => void go(() => put(`/v1/staff/${id}/assignment`, { roles: picked, facilityIds: sites }))}>Save</Button>}>
            <div className="grid grid-cols-1 gap-1 sm:grid-cols-3">{(roles.data ?? []).map((r) => <label key={r.key} className="flex items-center gap-2 text-xs"><input type="checkbox" checked={picked.includes(r.key)} onChange={(e) => setPicked(toggle(picked, r.key, e.target.checked))} /> {r.label}</label>)}</div>
            <div className="mt-3 grid grid-cols-1 gap-1 border-t border-line pt-3 sm:grid-cols-3">{me.facilities.map((x) => <label key={x.id} className="flex items-center gap-2 text-xs"><input type="checkbox" checked={sites.includes(x.id)} onChange={(e) => setSites(toggle(sites, x.id, e.target.checked))} /> {x.name}</label>)}</div>
          </Card>
          <Card title="Reset password">
            <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); void go(async () => { await post(`/v1/staff/${id}/reset-password`, { temporaryPassword: pw }); setPw(""); }); }}>
              <Field label="Temporary password"><Input type="password" minLength={12} required value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
              <div className="self-end"><Button type="submit" variant="secondary" busy={act.busy}>Reset</Button></div>
            </form>
          </Card>
        </>
      )}
    </Page>
  );
}
