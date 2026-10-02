import { redirect } from "next/navigation";
import { AuthPanel } from "@/components/auth-panel";
import { Icon, type IconName } from "@/components/icons";
import { switchableAccounts } from "@/lib/switch-actions";
import { readToken } from "@/lib/session";

const POINTS: { icon: IconName; title: string; body: string }[] = [
  { icon: "farms", title: "Farms and seasons", body: "Plots, crop cycles, stage calendars and yields in one record." },
  { icon: "workforce", title: "Workforce you can trust", body: "Server-timed tasks, attendance and fraud checks on every clock-in." },
  { icon: "money", title: "Trade and money", body: "Orders, payments, settlements and a ledger that balances." }
];

export default async function LoginPage() {
  const token = await readToken();
  if (token) {
    redirect("/");
  }
  const accounts = await switchableAccounts();

  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
      <aside className="relative hidden overflow-hidden bg-[var(--color-accent-deep)] p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="flex items-center gap-3">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white"><img src="/logo-icon.svg" alt="" aria-hidden="true" className="h-8 w-8" /></span>
          <span className="font-[family-name:var(--font-display)] text-2xl font-semibold">SmartSeason</span>
        </div>
        <div className="max-w-xl">
          <h1 className="text-4xl font-semibold leading-tight">
            Run the whole farm, from the field to the till.
          </h1>
          <ul className="mt-10 space-y-6">
            {POINTS.map((point) => (
              <li key={point.title} className="flex gap-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white/12 text-white">
                  <Icon name={point.icon} className="h-5 w-5" />
                </span>
                <span>
                  <span className="block text-lg font-semibold">{point.title}</span>
                  <span className="block text-base leading-snug text-white/75">{point.body}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-sm text-white/60">Agricultural operations platform</p>
      </aside>

      <main className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-6 lg:hidden">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/logo-icon.svg" alt="" aria-hidden="true" className="mb-3 h-12 w-12" />
          </div>
          <h2 className="text-3xl font-semibold">Welcome back</h2>
          <p className="mb-6 mt-1.5 text-base text-[var(--color-muted)]">
            Sign in to SmartSeason to see your farms, work and orders.
          </p>
          <AuthPanel accounts={accounts} />
        </div>
      </main>
    </div>
  );
}
