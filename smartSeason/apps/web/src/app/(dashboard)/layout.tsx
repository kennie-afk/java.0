import { redirect } from "next/navigation";
import { Rail, type RailItem } from "@/components/rail";
import { readToken } from "@/lib/session";

const ITEMS: RailItem[] = [
  { href: "/", label: "Overview", icon: "home" },
  { href: "/farms", label: "Farms", icon: "farms" },
  { href: "/seasons", label: "Seasons", icon: "seasons" },
  { href: "/workforce", label: "Workforce", icon: "workforce" },
  { href: "/marketplace", label: "Market", icon: "marketplace" },
  { href: "/fraud", label: "Fraud", icon: "fraud" }
];

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  return (
    <div className="flex min-h-screen bg-[var(--color-canvas)]">
      <Rail items={ITEMS} />
      <main className="flex-1 px-6 py-10 md:px-12 lg:px-16">
        <div className="mx-auto max-w-5xl">{children}</div>
      </main>
    </div>
  );
}
