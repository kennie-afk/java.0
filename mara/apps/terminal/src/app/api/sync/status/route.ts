import { forward } from "@/lib/upstream";

export const dynamic = "force-dynamic";

export const GET = (request: Request) => forward(request, "SYNC_BASE_URL", "GET", "/v1/terminal/sync/status");
