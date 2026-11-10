import { redirect } from "next/navigation";
import { Shell } from "@/components/nav";
import { enabledServices } from "@/lib/deployment";
import { readRoles, readToken } from "@/lib/session";

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }
  const roles = await readRoles();

  return (
    <Shell roles={roles} enabled={enabledServices()}>
      {children}
    </Shell>
  );
}
