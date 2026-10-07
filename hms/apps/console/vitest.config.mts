import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

// The console's logic (offline queue, API client, rules) runs in plain Node with the browser pieces stubbed per test:
// no DOM library is installed, and none of these tests renders a component.
export default defineConfig({
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  test: { environment: "node", include: ["src/**/*.test.ts"] }
});
