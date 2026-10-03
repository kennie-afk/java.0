"use client";

import { discard, flush, retry, useOutbox } from "@/lib/offline";
import { stamp } from "@/lib/format";
import { Button, Card, Confirm, Notice, Page, Status, Table, Td, Tr } from "@/components/ui";

export default function Sync() {
  const { items, online, refresh } = useOutbox();
  return (
    <Page title="Waiting to send" sub="Entries saved on this device while the connection was down." actions={<Button disabled={!online || items.length === 0} onClick={() => void flush().then(refresh)}>Send now</Button>}>
      {!online && <Notice tone="warn" title="You are offline">Entries stay on this device and send by themselves when the connection returns.</Notice>}
      <Card pad={false}>
        <Table head={["Entry", "Saved", "Status", ""]} empty="Nothing is waiting.">
          {items.map((q) => (
            <Tr key={q.id} tone={q.status === "FAILED" ? "danger" : undefined}>
              <Td>{q.label}{q.error && <div className="text-xs text-muted">{q.error}</div>}</Td>
              <Td>{stamp(new Date(q.createdAt).toISOString())}</Td>
              <Td><Status value={q.status} /></Td>
              <Td><span className="flex gap-1">
                {q.status === "FAILED" && <Button variant="secondary" onClick={() => void retry(q.id)}>Try again</Button>}
                <Confirm label="Discard" prompt="Discard this entry? It was never recorded, and this cannot be undone." onConfirm={() => discard(q.id)} />
              </span></Td>
            </Tr>
          ))}
        </Table>
      </Card>
      <p className="text-sm text-muted">These entries hold patient information and are stored in this browser until they are sent. Sign out on a shared computer only after they have gone.</p>
    </Page>
  );
}
