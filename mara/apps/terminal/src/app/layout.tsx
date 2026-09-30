import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Nav } from "@/components/nav";
import { SwRegister } from "@/components/sw-register";
import "./globals.css";

export const metadata: Metadata = {
  title: "Mara Terminal",
  description: "Offline-first point-of-sale terminal",
  manifest: "/manifest.webmanifest"
};

export const viewport: Viewport = { themeColor: "#b4441f", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="flex min-h-screen flex-col md:flex-row">
          <Nav />
          <main className="min-w-0 flex-1 p-3 sm:p-4">{children}</main>
        </div>
        <SwRegister />
      </body>
    </html>
  );
}
