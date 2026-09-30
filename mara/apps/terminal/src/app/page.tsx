"use client";

import Link from "next/link";
import { useStaffSession } from "@/components/use-session";
import { Badge, Card, KeyValue, LinkButton, Loading, Notice, PageHeader, SectionTitle } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";

export default function StatusPage() {
  const s = useTerminalStatus();
  const { session } = useStaffSession();
  if (!s.loaded) return <Loading />;

  return (
    <div className="space-y-4">
      <PageHeader
        title="Terminal status"
        sub="Everything on this page is read from this device. Nothing is fetched from a Mara server except the identity health probe."
        actions={
          s.identity ? (
            <LinkButton href="/sale" variant="primary">
              Open sales
            </LinkButton>
          ) : (
            <LinkButton href="/enrol" variant="primary">
              Enrol this terminal
            </LinkButton>
          )
        }
      />

      {s.storageError ? (
        <Notice tone="danger" title="Local storage is unavailable">
          {s.storageError}. This browser is blocking IndexedDB (a private window can do this). The till cannot record
          anything until that is fixed.
        </Notice>
      ) : null}

      <div className="grid gap-3 md:grid-cols-2">
        <Card>
          <SectionTitle>Identity</SectionTitle>
          {s.identity ? (
            <KeyValue
              rows={[
                ["Enrolment", <Badge key="e" tone="good">Enrolled</Badge>],
                ["Terminal id", <span key="t" className="font-mono">{s.identity.terminalId}</span>],
                ["Label", s.identity.label],
                ["Enrolled", new Date(s.identity.enrolledAt).toLocaleString()],
                ["Signing key", "Ed25519, held non-extractable in this browser"]
              ]}
            />
          ) : (
            <div className="space-y-2 text-xs">
              <Badge tone="warn">Not enrolled</Badge>
              <p className="text-muted">
                A terminal id comes only from identity-service at enrolment, and every journal entry is signed by the key
                created then. Until this device is enrolled it has no identity, so sales are blocked.
              </p>
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle>Connectivity</SectionTitle>
          <KeyValue
            rows={[
              ["Network (browser-reported)", s.online ? <Badge key="n" tone="good">Online</Badge> : <Badge key="n" tone="warn">Offline</Badge>],
              [
                "identity-service",
                !s.online ? (
                  "Not checked while offline"
                ) : s.identityConfigured === false ? (
                  <Badge key="i" tone="warn">Not configured (IDENTITY_BASE_URL)</Badge>
                ) : s.identityReachable === null ? (
                  "Checking..."
                ) : s.identityReachable ? (
                  <Badge key="i" tone="good">Reachable</Badge>
                ) : (
                  <Badge key="i" tone="warn">Unreachable</Badge>
                )
              ],
              ["Selling", "Never depends on either of the above."]
            ]}
          />
        </Card>

        <Card>
          <SectionTitle>Sync</SectionTitle>
          <Notice tone="warn" title="Sync service not built">
            Nothing has left this device. Sales live only in this browser&apos;s local journal until a sync-service exists to
            receive them. Clearing this browser&apos;s site data would destroy them, and no server holds a copy.
          </Notice>
        </Card>

        <Card>
          <SectionTitle>Fiscal numbering</SectionTitle>
          {s.lease ? (
            <KeyValue
              rows={[
                ["Lease", `${s.lease.firstNumber} to ${s.lease.lastNumber}`],
                ["Next number", s.lease.nextNumber],
                ["Expires", new Date(s.lease.expiresAtMs).toLocaleString()]
              ]}
            />
          ) : (
            <Notice tone="warn" title="No fiscal lease held">
              Fiscal numbers are leased to a terminal by the server, and the service that leases them is not built. Every
              sale is therefore recorded <strong>FISCAL_PENDING</strong>: the sale is real and signed, but it carries no tax
              invoice number yet.
            </Notice>
          )}
        </Card>

        <Card>
          <SectionTitle>Journal</SectionTitle>
          <KeyValue
            rows={[
              ["Entries", s.head ? String(s.head.lastSequence) : "0"],
              [
                "Head digest",
                s.head ? <span key="h" className="break-all font-mono text-2xs">{s.head.headDigest}</span> : "none yet"
              ],
              ["Catalogue items", String(s.items)]
            ]}
          />
          <div className="mt-2 flex gap-2">
            <LinkButton href="/journal">Journal</LinkButton>
            <LinkButton href="/journal/verify">Verify</LinkButton>
          </div>
        </Card>

        <Card>
          <SectionTitle>Staff sign-in</SectionTitle>
          {session ? (
            <KeyValue
              rows={[
                ["Signed in", `${session.displayName} (${session.role.toLowerCase()})`],
                ["Verified by", session.via === "server" ? "identity-service" : "this device, offline"]
              ]}
            />
          ) : (
            <Notice tone="info" title="Nobody is signed in">
              Sales are recorded against the person who makes them. <Link href="/signin" className="underline">Sign in</Link> with a staff number and PIN issued by the owner.
            </Notice>
          )}
        </Card>
      </div>
    </div>
  );
}
