import { redirect } from "next/navigation";
import { AuthPanel } from "@/components/auth-panel";
import { readToken } from "@/lib/session";

export default async function LoginPage() {
  const token = await readToken();
  if (token) {
    redirect("/");
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-6 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <h1 className="text-2xl font-semibold tracking-tight">SmartSeason</h1>
          <p className="mt-1 text-sm text-[var(--color-muted)]">
            Farms, seasons, workforce and trade.
          </p>
        </div>
        <AuthPanel />
      </div>
    </div>
  );
}
