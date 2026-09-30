import type { NextConfig } from "next";

const config: NextConfig = {
  agentRules: false,
  reactStrictMode: true,
  output: "standalone",
  poweredByHeader: false,
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" }
        ]
      },
      {
        // The worker must always be revalidated, or a bad one can never be replaced.
        source: "/sw.js",
        headers: [{ key: "Cache-Control", value: "no-cache" }]
      }
    ];
  }
};

export default config;
