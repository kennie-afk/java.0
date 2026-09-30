import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/* eslint-disable @typescript-eslint/no-explicit-any */
export const vectors: any = JSON.parse(
  readFileSync(fileURLToPath(new URL("./fixtures/java-vectors.json", import.meta.url)), "utf8")
);
