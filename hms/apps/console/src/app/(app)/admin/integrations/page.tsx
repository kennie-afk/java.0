"use client";

import { Card, KV, Notice, Page } from "@/components/ui";

export default function Integrations() {
  const base = typeof window === "undefined" ? "" : window.location.origin.replace(/:3600$/, ":8100");
  return (
    <Page title="Integrations" sub="Reading records from other systems. Nothing here can change a record.">
      <Card title="FHIR R4 read interface">
        <div className="space-y-2 text-sm">
          <KV k="Base URL" v={<code>{base}/fhir/r4</code>} />
          <KV k="Resources" v="Patient, Encounter, Observation (vital signs, validated laboratory results), MedicationRequest, AllergyIntolerance" />
          <KV k="Sign in" v={<>Create a staff account with the <b>Integration (FHIR read)</b> role, then call <code>POST /v1/auth/login</code> and send the token as <code>Authorization: Bearer</code>.</>} />
          <KV k="Restricted records" v={<>Send <code>X-Access-Reason</code> with a reason. It is written to the audit trail with the read.</>} />
        </div>
      </Card>
      <Notice title="What this is not">It maps what this system holds. It is not claimed to conform to any national implementation guide, and identifiers use a local urn. Unvalidated laboratory results and retracted vital signs are never returned.</Notice>
    </Page>
  );
}
