import { redirect } from "next/navigation";
import { Nav } from "@/components/nav";
import { readRoles, readToken } from "@/lib/session";

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }
  const roles = await readRoles();

  return (
    <div className="flex min-h-screen bg-[var(--color-canvas)]">
      <Nav roles={roles} />
      <main className="flex-1 px-3.5 py-7 md:px-8 lg:px-10">
        <div className="mx-auto max-w-6xl">{children}</div>
      </main>
    </div>
  );
}
