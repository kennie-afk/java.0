import type { Metadata } from "next";
import "@fontsource-variable/space-grotesk";
import "./globals.css";
import { DemoBanner } from "@/components/DemoBanner";
import { ServiceWorker } from "@/components/ServiceWorker";

export const metadata: Metadata = {
  title: "HMS",
  description: "Health management console",
  icons: { icon: "/logo-icon.flat.svg" }
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        {children}
        <DemoBanner />
        <ServiceWorker />
      </body>
    </html>
  );
}
