import { PortalShell } from "./PortalShell";

export const metadata = { title: "Patient portal" };

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell>{children}</PortalShell>;
}
