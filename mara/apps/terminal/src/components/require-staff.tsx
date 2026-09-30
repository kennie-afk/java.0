"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Loading, Notice } from "./ui";
import { useTerminalStatus } from "./use-status";
import { useStaffSession } from "./use-session";

/**
 * Sale screens need a person. An enrolled till with nobody signed in sells to nobody in
 * particular, and "who voided this" then has no answer. Not-yet-enrolled terminals fall
 * through, because those pages already explain what enrolment is for.
 */
export function RequireStaff({ children }: { children: ReactNode }) {
  const status = useTerminalStatus();
  const { loaded, session } = useStaffSession();
  if (!status.loaded || !loaded) return <Loading />;
  if (status.identity && !session) {
    return (
      <Notice tone="warn" title="Sign in to sell">
        A sale is recorded against the person who made it. <Link href="/signin" className="underline">Sign in with your staff number and PIN</Link>.
      </Notice>
    );
  }
  return <>{children}</>;
}
