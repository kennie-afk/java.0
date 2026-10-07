import { NextResponse, type NextRequest } from "next/server";
import { api } from "@/lib/api";
import { readSession } from "@/lib/session";

interface Row { id: string; name: string; county?: string; unit?: string; listPriceCents?: number; phone?: string }

const money = (cents: number) => `KSh ${(cents / 100).toLocaleString("en-KE")}`;

/** Search-as-you-type source for the pickers: at most 20 matches for whatever has been typed. */
export async function GET(request: NextRequest) {
  if (!(await readSession())) {
    return NextResponse.json({ error: "Sign in again." }, { status: 401 });
  }
  const kind = request.nextUrl.searchParams.get("kind");
  const q = (request.nextUrl.searchParams.get("q") ?? "").trim().slice(0, 80);
  if (kind !== "products" && kind !== "customers" && kind !== "suppliers") {
    return NextResponse.json({ error: "Unknown list." }, { status: 400 });
  }
  const params = new URLSearchParams({ limit: "20" });
  if (q) params.set("q", q);
  try {
    const { items } = await api.page<Row>(`/v1/${kind}?${params.toString()}`);
    return NextResponse.json(
      items.map((row) => ({
        value: row.id,
        label:
          kind === "products"
            ? `${row.name} · ${row.unit} · ${money(row.listPriceCents ?? 0)}`
            : kind === "customers"
              ? `${row.name} · ${row.county ?? ""}`
              : `${row.name} · ${row.county ?? ""}`
      }))
    );
  } catch {
    return NextResponse.json({ error: "Could not load the list." }, { status: 502 });
  }
}
