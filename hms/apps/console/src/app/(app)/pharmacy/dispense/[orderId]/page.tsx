"use client";

import { use, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiError, post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button, Card, ErrorNote, Field, Grid, Input, Page, useAction } from "@/components/ui";

export default function Dispense({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = use(params);
  const sp = useSearchParams();
  const { me } = useSession();
  const router = useRouter();
  const [qty, setQty] = useState(sp.get("left") ?? "");
  const [witness, setWitness] = useState("");
  const [override, setOverride] = useState<string | null>(null);
  const [needWitness, setNeedWitness] = useState(false);
  const { busy, error, run } = useAction();
  return (
    <Page title={`Dispense ${sp.get("name") ?? ""}`} sub="Stock is drawn first-expiry-first-out. Expired stock is never offered.">
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); void run(async () => {
        try {
          await post("/v1/pharmacy/dispense", { orderId, quantity: Number(qty), witnessId: witness || undefined, allergyOverrideReason: override ?? undefined });
          router.push("/pharmacy");
        } catch (err) {
          const a = err as ApiError;
          if (a.code === "allergy_conflict") setOverride("");
          if (a.code === "witness_required" || a.code === "witness_invalid") setNeedWitness(true);
          throw err;
        }
      }); }}>
        <Card><Grid cols={3}>
          <Field label="Quantity"><Input type="number" step="0.01" required value={qty} onChange={(e) => setQty(e.target.value)} /></Field>
          {needWitness && <Field label="Witness (staff id)" hint={`A controlled drug needs a second person. Your id: ${me.practitionerId}`}><Input required value={witness} onChange={(e) => setWitness(e.target.value)} /></Field>}
        </Grid>
        {override !== null && (
          <div className="mt-3 space-y-2 rounded-md border border-danger bg-danger-soft p-2">
            <p className="text-xs text-danger">The product matches a recorded allergy. To dispense anyway, state why.</p>
            <Input required minLength={10} placeholder="Reason (at least 10 characters)" value={override} onChange={(e) => setOverride(e.target.value)} />
          </div>
        )}</Card>
        <ErrorNote error={error} />
        <Button type="submit" busy={busy}>Dispense</Button>
      </form>
    </Page>
  );
}
