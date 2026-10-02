import { NextResponse } from "next/server";
import { forward } from "@/lib/upstream";

export const dynamic = "force-dynamic";

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  if (!/^[0-9]{1,18}$/.test(id)) return NextResponse.json({ error: "bad_request" }, { status: 400 });
  return forward(request, "CORE_BASE_URL", "POST", `/v1/terminal/fiscal/leases/${id}/return`);
}
