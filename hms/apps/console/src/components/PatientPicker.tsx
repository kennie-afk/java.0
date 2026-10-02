"use client";

import { useEffect, useState } from "react";
import { api, type Slice } from "@/lib/api";
import { age, date } from "@/lib/format";
import { Input } from "./ui";

export type PatientRow = { id: string; givenName: string; familyName: string; sex: string; birthDate: string; phone?: string; mrn?: string; restricted: boolean };

/** Type a name, MRN, ID number or phone; pick one. Searches server-side, one page. */
export function PatientPicker({ value, onPick }: { value: PatientRow | null; onPick: (p: PatientRow | null) => void }) {
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<PatientRow[]>([]);
  useEffect(() => {
    if (q.trim().length < 2) {
      setRows([]);
      return;
    }
    const t = setTimeout(() => {
      api<Slice<PatientRow>>(`/v1/patients?q=${encodeURIComponent(q.trim())}&limit=8`).then((s) => setRows(s.data)).catch(() => setRows([]));
    }, 250);
    return () => clearTimeout(t);
  }, [q]);

  if (value) {
    return (
      <div className="flex items-center justify-between rounded-md border border-line bg-raised px-2 py-1 text-xs">
        <span><b>{value.givenName} {value.familyName}</b> <span className="text-muted">{value.mrn} · {age(value.birthDate)} · {value.sex}</span></span>
        <button type="button" className="text-accent hover:underline" onClick={() => onPick(null)}>Change</button>
      </div>
    );
  }
  return (
    <div className="space-y-1">
      <Input placeholder="Search name, MRN, ID or phone" value={q} onChange={(e) => setQ(e.target.value)} />
      {rows.length > 0 && (
        <ul className="rounded-md border border-line bg-surface">
          {rows.map((p) => (
            <li key={p.id}>
              <button type="button" onClick={() => onPick(p)} className="flex w-full justify-between px-2 py-1 text-left text-xs hover:bg-raised">
                <span className="font-medium">{p.givenName} {p.familyName}</span>
                <span className="text-muted">{p.mrn} · {date(p.birthDate)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
