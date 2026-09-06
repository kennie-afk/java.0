import { redirect } from "next/navigation";
import { readSession } from "@/lib/session";
import { AuthPanel } from "@/app/login/panel";
import { MilkingScene } from "@/components/milking-scene";

export default async function LoginPage() {
  if (await readSession()) {
    redirect("/");
  }

  return (
    <main className="min-h-screen bg-[var(--color-canvas)]">
      <div className="mx-auto grid min-h-screen max-w-6xl items-center gap-12 px-6 py-12 lg:grid-cols-2">
        <section className="hidden lg:block">
          <h1 className="text-[2.5rem] font-semibold leading-[1.1] tracking-[-0.03em]">
            Sell the milk before
            <br />
            you ever hold it.
          </h1>
          <p className="mt-5 max-w-md text-[0.9375rem] leading-relaxed text-[var(--color-muted)]">
            Soko routes every order line to the cheapest supplier that can keep the cold chain and
            still deliver with shelf life to spare. You carry no stock and no spoilage.
          </p>
          <MilkingScene className="mt-8 w-full max-w-md rounded-xl" />

          <dl className="mt-8 grid max-w-md grid-cols-3 gap-5">
            <div>
              <dt className="text-[0.6875rem] uppercase tracking-wide text-[var(--color-faint)]">Routing</dt>
              <dd className="mt-1 text-[0.875rem] font-medium">Cheapest viable</dd>
            </div>
            <div>
              <dt className="text-[0.6875rem] uppercase tracking-wide text-[var(--color-faint)]">Cold chain</dt>
              <dd className="mt-1 text-[0.875rem] font-medium">Enforced</dd>
            </div>
            <div>
              <dt className="text-[0.6875rem] uppercase tracking-wide text-[var(--color-faint)]">Stock</dt>
              <dd className="mt-1 text-[0.875rem] font-medium">Never oversold</dd>
            </div>
          </dl>
        </section>

        <section className="mx-auto w-full max-w-[400px]">
          <AuthPanel />
        </section>
      </div>
    </main>
  );
}
