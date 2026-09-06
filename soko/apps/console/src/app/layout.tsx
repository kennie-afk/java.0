import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Soko",
  description: "Dropshipping for dairy and farm produce"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
