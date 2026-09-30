"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Card, Field, Input, Loading, LinkButton, Notice, PageHeader } from "@/components/ui";
import { RequireStaff } from "@/components/require-staff";
import { useTerminalStatus } from "@/components/use-status";
import { openTab } from "@/lib/tab-store";

function NewTabPageInner() {
  const s = useTerminalStatus();
  const router = useRouter();
  const [label, setLabel] = useState("");
  const [busy, setBusy] = useState(false);
  if (!s.loaded) return <Loading />;
  if (!s.identity) {
    return <Notice tone="warn" title="This terminal is not enrolled">Enrol it first; see Enrolment.</Notice>;
  }
  return (
    <form
      className="max-w-xl space-y-3"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        const tab = await openTab(label);
        router.push(`/sale/tab?id=${encodeURIComponent(tab.id)}`);
      }}
    >
      <PageHeader title="New tab" />
      <Card>
        <Field label="Label (optional)" hint='For example "Table 4" or "Room 12". Local only; not part of the signed sale.'>
          <Input value={label} maxLength={40} onChange={(e) => setLabel(e.target.value)} autoFocus autoComplete="off" />
        </Field>
      </Card>
      <div className="flex gap-2">
        <Button variant="primary" type="submit" disabled={busy}>Open tab</Button>
        <LinkButton href="/sale">Cancel</LinkButton>
      </div>
    </form>
  );
}

export default function NewTabPage() {
  return (
    <RequireStaff>
      <NewTabPageInner />
    </RequireStaff>
  );
}
