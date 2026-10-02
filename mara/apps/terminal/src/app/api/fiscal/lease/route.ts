import { forward } from "@/lib/upstream";

export const dynamic = "force-dynamic";

export const POST = (request: Request) => forward(request, "CORE_BASE_URL", "POST", "/v1/terminal/fiscal/leases");
