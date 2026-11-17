import { NextResponse } from "next/server";
import { loadSummary } from "@/lib/cases";
import { readToken } from "@/lib/session";

/** Counts for the menu badge. Cheap by design: four indexed counts on the server. */
export async function GET() {
  if (!(await readToken())) return new NextResponse(null, { status: 401 });
  return NextResponse.json(await loadSummary(), { headers: { "Cache-Control": "private, no-store" } });
}
