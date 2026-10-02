"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError, post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, Select, useAction } from "@/components/ui";

type Match = { id: string; givenName: string; familyName: string; birthDate: string; mrn?: string; reason: string };

export default function NewPatient() {
  const { facilityId } = useSession();
  const router = useRouter();
  const [f, setF] = useState({ givenName: "", otherNames: "", familyName: "", sex: "FEMALE", birthDate: "", phone: "", county: "", nationalId: "", shaNumber: "", nokName: "", nokPhone: "", nokRel: "Spouse" });
  const [matches, setMatches] = useState<Match[] | null>(null);
  const { busy, error, run } = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  const submit = (confirmNotDuplicate: boolean) =>
    run(async () => {
      const identifiers = [
        ...(f.nationalId ? [{ system: "NATIONAL_ID", value: f.nationalId }] : []),
        ...(f.shaNumber ? [{ system: "SHA_NUMBER", value: f.shaNumber }] : [])
      ];
      try {
        const p = await post<{ id: string }>("/v1/patients", {
          facilityId, confirmNotDuplicate, identifiers,
          demographics: { givenName: f.givenName, otherNames: f.otherNames || undefined, familyName: f.familyName, sex: f.sex, birthDate: f.birthDate, phone: f.phone || undefined, county: f.county || undefined },
          contacts: f.nokName ? [{ relationship: f.nokRel, fullName: f.nokName, phone: f.nokPhone || undefined }] : []
        });
        router.push(`/patients/${p.id}`);
      } catch (e) {
        const err = e as ApiError;
        if (err.code === "possible_duplicate") {
          setMatches(err.body.candidates as Match[]);
          return;
        }
        throw e;
      }
    });

  return (
    <Page title="Register patient">
      {matches && (
        <Card title="This person may already be registered">
          <ul className="space-y-1 text-sm">
            {matches.map((m) => (
              <li key={m.id} className="flex justify-between">
                <a className="text-accent hover:underline" href={`/patients/${m.id}`}>{m.givenName} {m.familyName} · {m.mrn} · born {m.birthDate}</a>
                <span className="text-muted">{m.reason.replaceAll("_", " ").toLowerCase()}</span>
              </li>
            ))}
          </ul>
          {matches.some((m) => m.reason === "IDENTIFIER") ? (
            <p className="pt-2 text-sm text-danger">The same ID number is already registered. Open that record instead.</p>
          ) : (
            <div className="flex gap-2 pt-2">
              <Button variant="secondary" onClick={() => setMatches(null)}>Edit details</Button>
              <Button busy={busy} onClick={() => void submit(true)}>This is a different person: register</Button>
            </div>
          )}
        </Card>
      )}
      <form onSubmit={(e) => { e.preventDefault(); void submit(false); }} className="space-y-4">
        <Card title="Details">
          <Grid>
            <Field label="Given name"><Input required value={f.givenName} onChange={set("givenName")} /></Field>
            <Field label="Other names"><Input value={f.otherNames} onChange={set("otherNames")} /></Field>
            <Field label="Family name"><Input required value={f.familyName} onChange={set("familyName")} /></Field>
            <Field label="Sex"><Select value={f.sex} onChange={set("sex")}><option>FEMALE</option><option>MALE</option><option>INTERSEX</option><option>UNKNOWN</option></Select></Field>
            <Field label="Date of birth"><Input type="date" required value={f.birthDate} onChange={set("birthDate")} /></Field>
            <Field label="Phone"><Input placeholder="07xx xxx xxx" value={f.phone} onChange={set("phone")} /></Field>
            <Field label="County"><Input value={f.county} onChange={set("county")} /></Field>
            <Field label="National ID" hint="7 or 8 digits"><Input value={f.nationalId} onChange={set("nationalId")} /></Field>
            <Field label="SHA number"><Input value={f.shaNumber} onChange={set("shaNumber")} /></Field>
          </Grid>
        </Card>
        <Card title="Next of kin">
          <Grid>
            <Field label="Name"><Input value={f.nokName} onChange={set("nokName")} /></Field>
            <Field label="Relationship"><Input value={f.nokRel} onChange={set("nokRel")} /></Field>
            <Field label="Phone"><Input value={f.nokPhone} onChange={set("nokPhone")} /></Field>
          </Grid>
        </Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Register</Button>
      </form>
    </Page>
  );
}
