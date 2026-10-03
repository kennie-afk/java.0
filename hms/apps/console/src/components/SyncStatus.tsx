"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useOutbox } from "@/lib/offline";

/** Shows the connection and what is waiting to be sent. Silent when online with nothing waiting. */
export function SyncStatus() {
  const { online, pending, failed } = useOutbox();
  const [copyAt, setCopyAt] = useState<number | null>(null);
  useEffect(() => {
    const on = (e: Event) => setCopyAt((e as CustomEvent<number | null>).detail);
    window.addEventListener("hms-offline-copy", on);
    return () => window.removeEventListener("hms-offline-copy", on);
  }, []);
  const copy = copyAt ? <div className="mx-2.5 mb-2 rounded-md border border-warn px-2.5 py-1.5 text-xs font-semibold text-warn" role="status">Showing saved data from {new Date(copyAt).toLocaleTimeString("en-KE", { hour: "2-digit", minute: "2-digit" })}. It may be out of date.</div> : null;
  if (online && pending === 0 && failed === 0) return copy;
  const tone = failed > 0 ? "border-danger text-danger" : "border-warn text-warn";
  return (
    <>
    {copy}
    <Link href="/sync" className={`mx-2.5 mb-2 block rounded-md border px-2.5 py-1.5 text-xs font-semibold ${tone}`} role="status">
      {!online && "Offline. "}
      {pending > 0 && `${pending} waiting to send. `}
      {failed > 0 && `${failed} could not be sent. `}
      <span className="underline">Review</span>
    </Link>
    </>
  );
}
