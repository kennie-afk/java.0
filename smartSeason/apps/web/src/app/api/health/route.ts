import { NextResponse } from "next/server";

// Liveness only: the container is serving. It deliberately does not call the
// gateway, so a backend outage does not make the web tier look dead and get
// restarted underneath users who could still be shown a proper error page.
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ status: "UP" });
}
