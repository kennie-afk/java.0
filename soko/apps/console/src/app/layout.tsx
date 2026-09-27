import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "FreshFerm",
  description: "Dropshipping for fermented dairy drinks"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
