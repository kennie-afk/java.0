import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

// Tells the console whether this deployment is a demo, so the banner is a runtime flag, not a build one.
export function GET() {
  return NextResponse.json({ demo: process.env.HMS_DEMO_MODE === "true" });
}
