import { NextResponse, type NextRequest } from "next/server";
import { readToken } from "@/lib/session";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Streams a case photograph with the caller's own token, so the browser never needs it. */
export async function GET(_request: NextRequest, { params }: { params: Promise<{ id: string; photoId: string }> }) {
  const { id, photoId } = await params;
  const token = await readToken();
  if (!token) return new NextResponse(null, { status: 401 });
  if (!UUID.test(id) || !UUID.test(photoId)) return new NextResponse(null, { status: 404 });
  const upstream = await fetch(`${GATEWAY}/api/agronomy/v1/cases/${id}/photos/${photoId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store"
  });
  if (!upstream.ok) return new NextResponse(null, { status: upstream.status === 404 ? 404 : 502 });
  return new NextResponse(upstream.body, {
    headers: {
      "Content-Type": upstream.headers.get("content-type") ?? "image/jpeg",
      "Cache-Control": "private, no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}
