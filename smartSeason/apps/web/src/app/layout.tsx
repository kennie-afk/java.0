import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "SmartSeason",
  description: "Agricultural operations platform",
  icons: { icon: "/logo-icon.flat.svg" }
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
