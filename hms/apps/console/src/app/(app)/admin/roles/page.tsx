"use client";

import { useState } from "react";
import { api, post, put, useFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Badge, Button, Card, ErrorNote, Field, Grid, Input, Loading, Page, Table, Td, Tr, useAction } from "@/components/ui";

type Role = { key: string; label: string; description?: string; system: boolean; permissions: string[]; members: number };

export default function Roles() {
  const { can, me } = useSession();
  const roles = useFetch<Role[]>("/v1/roles");
  const perms = useFetch<string[]>("/v1/permissions");
  const [edit, setEdit] = useState<{ key: string; label: string; permissions: string[]; isNew: boolean } | null>(null);
  const act = useAction();
  const manage = can("roles:manage");
  const save = () => act.run(async () => {
    if (!edit) return;
    if (edit.isNew) await post("/v1/roles", { key: edit.key, label: edit.label, permissions: edit.permissions });
    else await put(`/v1/roles/${edit.key}`, { label: edit.label, permissions: edit.permissions });
    setEdit(null);
    await roles.reload();
  });
  if (edit) {
    const grouped = new Map<string, string[]>();
    (perms.data ?? []).forEach((p) => grouped.set(p.split(":")[0], [...(grouped.get(p.split(":")[0]) ?? []), p]));
    return (
      <Page title={edit.isNew ? "New role" : `Edit ${edit.label}`}>
        <Card><Grid cols={2}>
          <Field label="Key" hint="Upper case, e.g. WARD_CLERK"><Input disabled={!edit.isNew} value={edit.key} onChange={(e) => setEdit({ ...edit, key: e.target.value.toUpperCase() })} /></Field>
          <Field label="Label"><Input value={edit.label} onChange={(e) => setEdit({ ...edit, label: e.target.value })} /></Field>
        </Grid></Card>
        <Card title="Permissions (you can only grant what you hold)">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {[...grouped.entries()].map(([g, list]) => (
              <div key={g}><div className="pb-1 text-xs font-semibold uppercase tracking-[0.06em] text-muted">{g}</div>
                {list.map((p) => <label key={p} className="flex items-center gap-2 text-sm"><input type="checkbox" disabled={!me.permissions.includes(p)} checked={edit.permissions.includes(p)} onChange={(e) => setEdit({ ...edit, permissions: e.target.checked ? [...edit.permissions, p] : edit.permissions.filter((x) => x !== p) })} /> {p}</label>)}</div>
            ))}
          </div>
        </Card>
        <ErrorNote error={act.error} />
        <div className="flex gap-2"><Button busy={act.busy} onClick={() => void save()}>Save</Button><Button variant="secondary" onClick={() => setEdit(null)}>Cancel</Button></div>
      </Page>
    );
  }
  return (
    <Page title="Roles" actions={manage && <Button onClick={() => setEdit({ key: "", label: "", permissions: [], isNew: true })}>New role</Button>}>
      <ErrorNote error={act.error} />
      <Card pad={false}>
        {roles.loading ? <Loading /> : (
          <Table head={["Role", "Members", "Permissions", ""]} empty="No roles.">
            {(roles.data ?? []).map((r) => (
              <Tr key={r.key}><Td>{r.label} {r.system && <Badge>Standard</Badge>}</Td><Td>{r.members}</Td><Td className="max-w-md text-muted">{r.key === "ORG_ADMIN" ? "Everything, always" : r.permissions.join(", ")}</Td>
                <Td>{manage && r.key !== "ORG_ADMIN" && <span className="flex gap-1"><Button variant="secondary" onClick={() => setEdit({ key: r.key, label: r.label, permissions: r.permissions, isNew: false })}>Edit</Button>
                  {!r.system && r.members === 0 && <Button variant="danger" onClick={() => void act.run(async () => { await api(`/v1/roles/${r.key}`, { method: "DELETE" }); await roles.reload(); })}>Delete</Button>}</span>}</Td></Tr>
            ))}
          </Table>
        )}
      </Card>
    </Page>
  );
}
