"use client";

import { use, useState } from "react";
import { useRouter } from "next/navigation";
import { post, useFetch } from "@/lib/api";
import { date } from "@/lib/format";
import { useSession } from "@/lib/session";
import { duplicateCheckBody, mergeReady, MERGE_REASON_MIN, MERGE_WORD } from "@/lib/rules";
import { PatientPicker, type PatientRow } from "@/components/PatientPicker";
import { Button, Card, ErrorNote, Field, Input, KV, Loading, Notice, Page, Table, Td, Tr, useAction } from "@/components/ui";

type Patient = { id: string; givenName: string; otherNames?: string; familyName: string; sex: string; birthDate: string; phone?: string; mergedInto?: string; identifiers: { system: string; value: string }[] };
type Match = { id: string; givenName: string; familyName: string; birthDate: string; sex: string; phone?: string; score: number; reason: string; mrn?: string };

const REASONS: Record<string, string> = { IDENTIFIER: "Same identifier", NAME_AND_BIRTH_DATE: "Same name and birth date", NAME_AND_PHONE: "Similar name, same phone" };

/** This record is the duplicate: it is folded into the one chosen here. Nothing is deleted, but it cannot be undone from the console. */
export default function Merge({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { can } = useSession();
  const router = useRouter();
  const p = useFetch<Patient>(`/v1/patients/${id}`);
  const checkable = can("patients:write");
  const [matches, setMatches] = useState<Match[] | null>(null);
  const [survivor, setSurvivor] = useState<{ id: string; label: string } | null>(null);
  const [reason, setReason] = useState("");
  const [typed, setTyped] = useState("");
  const check = useAction();
  const merge = useAction();

  if (!can("patients:merge")) return <Page title="Merge records"><Notice tone="warn">You do not have permission to merge patient records.</Notice></Page>;
  if (!p.data) return <Page title="Merge records">{p.error ? <ErrorNote error={p.error} /> : <Loading />}</Page>;
  const d = p.data;
  const pick = (m: { id: string; givenName: string; familyName: string; mrn?: string }) => setSurvivor({ id: m.id, label: `${m.givenName} ${m.familyName}${m.mrn ? ` (${m.mrn})` : ""}` });
  return (
    <Page title="Merge duplicate record" sub="Use this when two records are the same person. Choose the record to keep.">
      {d.mergedInto && <Notice tone="warn">This record has already been merged into another.</Notice>}
      <Card title="Record to merge away (the duplicate)">
        <div className="grid gap-3 sm:grid-cols-3">
          <KV k="Name" v={`${d.givenName} ${d.otherNames ?? ""} ${d.familyName}`} /><KV k="Born" v={date(d.birthDate)} /><KV k="Sex" v={d.sex} />
        </div>
      </Card>
      <Card title="Possible matches" actions={checkable && <Button variant="secondary" busy={check.busy} onClick={() => void check.run(async () => {
        const all = await post<Match[]>("/v1/patients/duplicate-check", duplicateCheckBody(d));
        setMatches(all.filter((m) => m.id !== id));
      })}>Check for duplicates</Button>} pad={false}>
        <ErrorNote error={check.error} />
        {!checkable && <div className="p-3 text-sm text-muted">The duplicate check needs the permission to write patient records. You can still pick the record to keep by searching.</div>}
        {matches && (
          <Table head={["Name", "MRN", "Born", "Why", ""]} empty="No likely duplicates were found.">
            {matches.map((m) => (
              <Tr key={m.id}><Td>{m.givenName} {m.familyName}</Td><Td>{m.mrn}</Td><Td>{date(m.birthDate)}</Td><Td>{REASONS[m.reason] ?? m.reason}</Td>
                <Td><Button variant="secondary" onClick={() => pick(m)}>Keep this one</Button></Td></Tr>
            ))}
          </Table>
        )}
      </Card>
      <Card title="Record to keep (the survivor)">
        {survivor ? (
          <div className="flex items-center justify-between text-sm"><b>{survivor.label}</b><button type="button" className="text-accent hover:underline" onClick={() => setSurvivor(null)}>Change</button></div>
        ) : <PatientPicker value={null} onPick={(r: PatientRow | null) => r && pick(r)} />}
      </Card>
      {survivor && (
        <Card title="Confirm">
          <div className="space-y-3">
            <Notice tone="warn" title="This cannot be undone from here">
              The record for {d.givenName} {d.familyName} will be closed and point to {survivor.label}. Its identifiers and contacts move across; visits and results stay where they were recorded. The merge is audited with your reason.
            </Notice>
            <Field label={`Reason (at least ${MERGE_REASON_MIN} characters)`}><Input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
            <Field label={`Type ${MERGE_WORD} to confirm`}><Input value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" className="max-w-40" /></Field>
            <ErrorNote error={merge.error} />
            <Button variant="danger" busy={merge.busy} disabled={!mergeReady({ typed, reason, duplicateId: id, survivorId: survivor.id })}
              onClick={() => void merge.run(async () => { await post(`/v1/patients/${id}/merge`, { survivorId: survivor.id, reason: reason.trim() }); router.push(`/patients/${survivor.id}`); })}>Merge records</Button>
          </div>
        </Card>
      )}
    </Page>
  );
}
