import { redirect } from "next/navigation";
import { Nav } from "@/components/nav";
import { enabledServices } from "@/lib/deployment";
import { readRoles, readToken } from "@/lib/session";

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }
  const roles = await readRoles();

  return (
    <div className="flex min-h-screen flex-col bg-[var(--color-canvas)] lg:flex-row">
      <Nav roles={roles} enabled={enabledServices()} />
      <main className="min-w-0 flex-1 px-4 pb-20 pt-5 sm:px-6 lg:px-8 lg:pt-7">
        <div className="mx-auto w-full max-w-[1680px]">{children}</div>
      </main>
    </div>
  );
}
