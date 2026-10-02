"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { post, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Role = { key: string; label: string };
const CADRES = ["DOCTOR", "CLINICAL_OFFICER", "NURSE", "MIDWIFE", "PHARMACIST", "PHARMACEUTICAL_TECHNOLOGIST", "LAB_TECHNOLOGIST", "RADIOGRAPHER", "NUTRITIONIST", "PHYSIOTHERAPIST", "DENTIST", "COMMUNITY_HEALTH", "RECORDS_OFFICER", "ACCOUNTANT", "ADMINISTRATIVE"];

export default function NewStaff() {
  const { me } = useSession();
  const router = useRouter();
  const roles = useFetch<Role[]>("/v1/roles");
  const [f, setF] = useState({ fullName: "", email: "", cadre: "NURSE", licenceBody: "", licenceNo: "", phone: "", password: "" });
  const [picked, setPicked] = useState<string[]>([]);
  const [sites, setSites] = useState<string[]>(me.facilities.slice(0, 1).map((x) => x.id));
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  const toggle = (list: string[], v: string, on: boolean) => (on ? [...list, v] : list.filter((x) => x !== v));
  return (
    <Page title="Add staff member">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => { const s = await post<{ id: string }>("/v1/staff", { fullName: f.fullName, email: f.email, cadre: f.cadre, licenceBody: f.licenceBody || undefined, licenceNo: f.licenceNo || undefined, phone: f.phone || undefined, temporaryPassword: f.password, roles: picked, facilityIds: sites }); router.push(`/admin/staff/${s.id}`); }); }}>
        <Card title="Person"><Grid>
          <Field label="Full name"><Input required value={f.fullName} onChange={set("fullName")} /></Field>
          <Field label="Email"><Input type="email" required value={f.email} onChange={set("email")} /></Field>
          <Field label="Phone"><Input value={f.phone} onChange={set("phone")} /></Field>
          <Field label="Cadre"><Select value={f.cadre} onChange={set("cadre")}>{CADRES.map((c) => <option key={c}>{c}</option>)}</Select></Field>
          <Field label="Licensing body"><Select value={f.licenceBody} onChange={set("licenceBody")}><option value="">None</option>{["KMPDC", "NCK", "PPB", "KMLTTB", "COC", "OTHER"].map((c) => <option key={c}>{c}</option>)}</Select></Field>
          <Field label="Licence number"><Input value={f.licenceNo} onChange={set("licenceNo")} /></Field>
          <Field label="Temporary password" hint="At least 12 characters. Tell them to change it."><Input type="password" required minLength={12} value={f.password} onChange={set("password")} /></Field>
        </Grid></Card>
        <Card title="Roles"><div className="grid grid-cols-1 gap-1 sm:grid-cols-3">{(roles.data ?? []).map((r) => <label key={r.key} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={picked.includes(r.key)} onChange={(e) => setPicked(toggle(picked, r.key, e.target.checked))} /> {r.label}</label>)}</div></Card>
        <Card title="Works at"><div className="grid grid-cols-1 gap-1 sm:grid-cols-3">{me.facilities.map((x) => <label key={x.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={sites.includes(x.id)} onChange={(e) => setSites(toggle(sites, x.id, e.target.checked))} /> {x.name}</label>)}</div></Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy} disabled={picked.length === 0 || sites.length === 0}>Create</Button>
      </form>
    </Page>
  );
}
