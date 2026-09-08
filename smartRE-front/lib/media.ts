/**
 * Turns a stored media URL into one that works from both sides of the app.
 *
 * Uploaded files are stored with an absolute URL built from
 * NEXT_PUBLIC_API_URL — `http://localhost:8080/...` in local development. That
 * is correct for a browser and wrong for the server: Next's image optimiser
 * runs inside the container, where `localhost:8080` is the container's own
 * loopback and nothing is listening. The optimiser fails with ECONNREFUSED and
 * the image renders as nothing.
 *
 * Stripping our own origin leaves a root-relative path, which the optimiser
 * resolves against itself and the rewrite in next.config.js forwards to the
 * gateway over the Docker network. The browser is unaffected: a relative path
 * resolves to the same file it always did.
 *
 * URLs on any other host are returned untouched, so external images keep
 * working — they need an entry in `images.remotePatterns` to be optimised.
 */
const OWN_ORIGINS = [
  process.env.NEXT_PUBLIC_API_URL,
  process.env.NEXT_PUBLIC_S3_PUBLIC_URL,
].filter((value): value is string => Boolean(value));

export function mediaSrc(url: string | null | undefined): string {
  if (!url) return "";

  for (const origin of OWN_ORIGINS) {
    if (url.startsWith(origin)) {
      const rest = url.slice(origin.length);
      return rest.startsWith("/") ? rest : `/${rest}`;
    }
  }
  return url;
}

/**
 * True when a URL points somewhere we do not control, so it cannot be assumed
 * to be in the optimiser's allowlist. Callers render these with
 * `unoptimized` rather than letting the optimiser reject them with a 400.
 */
export function isExternalMedia(url: string | null | undefined): boolean {
  if (!url) return false;
  if (url.startsWith("/") || url.startsWith("data:") || url.startsWith("blob:")) return false;
  return !OWN_ORIGINS.some((origin) => url.startsWith(origin));
}
