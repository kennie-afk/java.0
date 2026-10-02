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
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&family=Manrope:wght@400;500;600;700;800&display=swap"
        />
      </head>
      <body className="min-h-screen antialiased">{children}
        <DemoBanner />
      </body>
    </html>
  );
}
