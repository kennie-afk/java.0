import { NextResponse } from "next/server";
import { forward } from "@/lib/upstream";

export const dynamic = "force-dynamic";

// The terminal names its lease request (a signed query parameter) so a retry after a lost
// response returns the same block instead of taking another. The id is validated here and
// again by the server; the upstream path stays one fixed route plus that one parameter.
const REQUEST_ID = /^[A-Za-z0-9-]{8,64}$/;

export const POST = (request: Request) => {
  const id = new URL(request.url).searchParams.get("request");
  if (id !== null && !REQUEST_ID.test(id)) {
    return NextResponse.json({ error: "bad_request", message: "Invalid request id." }, { status: 400 });
  }
  return forward(request, "CORE_BASE_URL", "POST", "/v1/terminal/fiscal/leases" + (id ? `?request=${id}` : ""));
};
