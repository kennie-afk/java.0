import { redirect } from "next/navigation";
import { AuthPanel } from "@/components/auth-panel";
import { switchableAccounts } from "@/lib/switch-actions";
import { readToken } from "@/lib/session";

export default async function LoginPage() {
  const token = await readToken();
  if (token) {
    redirect("/");
  }
  const accounts = await switchableAccounts();

  return (
    <div className="flex min-h-screen items-center justify-center px-3.5 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo-icon.svg" alt="" aria-hidden="true" className="mb-3 h-14 w-14" />
          <h1 className="text-2xl font-semibold tracking-tight">SmartSeason</h1>
          <p className="mt-1 text-sm text-[var(--color-muted)]">
            Farms, seasons, workforce and trade.
          </p>
        </div>
        <AuthPanel accounts={accounts} />
      </div>
    </div>
  );
}
