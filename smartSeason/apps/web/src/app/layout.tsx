import type { Metadata } from "next";
import "./globals.css";
import { DemoBanner } from "@/components/demo-banner";

// Read DEMO_MODE per request, not at build, so the same image runs either way.
export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "SmartSeason",
  description: "Agricultural operations platform",
  icons: { icon: "/logo-icon.flat.svg" }
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">{children}
        <DemoBanner />
      </body>
    </html>
  );
}
