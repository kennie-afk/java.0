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
    <main className="flex min-h-screen flex-col items-center justify-center bg-surface-2 p-4">
      <div className="w-full max-w-md">
        <div className="mb-4 flex items-center justify-center gap-2.5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo-icon.flat.svg" alt="" aria-hidden="true" className="h-[26px] w-[26px]" />
          <span className="font-display text-lg font-bold leading-none">
            <span className="text-gray-900 dark:text-white">Smart</span>
            <span className="text-brand-500">Season</span>
          </span>
        </div>
        <AuthPanel accounts={accounts} />
      </div>
    </main>
  );
}
