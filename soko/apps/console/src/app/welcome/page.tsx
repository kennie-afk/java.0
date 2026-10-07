import { Storefront, type PublicProduct } from "@/app/welcome/storefront";

// Forced dynamic on purpose: this page reads STOREFRONT_TENANT_SLUG and calls
// the live API at request time. Without this, Next.js prerenders it once as
// static HTML during the Docker build -- before docker-compose's runtime
// environment even exists and before there is any data to fetch -- and then
// serves that same stale, empty-catalogue page forever regardless of what
// actually changes in the running system.
export const dynamic = "force-dynamic";

// The public API pages its list (at most 200 a page). Read every page rather than silently
// showing only the first; the cap is a safety bound for a runaway catalogue, not a normal limit.
const PAGE = 200;
const MAX_PAGES = 10;

async function fetchCatalogue(): Promise<PublicProduct[]> {
  const api = process.env.SOKO_API_URL ?? "http://127.0.0.1:8090";
  const slug = process.env.STOREFRONT_TENANT_SLUG;
  if (!slug) {
    return [];
  }
  const all: PublicProduct[] = [];
  try {
    for (let page = 0; page < MAX_PAGES; page += 1) {
      const response = await fetch(`${api}/v1/public/${slug}/products?limit=${PAGE}&page=${page}`, {
        cache: "no-store",
      });
      if (!response.ok) {
        break;
      }
      all.push(...((await response.json()) as PublicProduct[]));
      if (response.headers.get("X-Has-More") !== "true") {
        break;
      }
    }
    return all;
  } catch {
    return all;
  }
}

export default async function WelcomePage() {
  const products = await fetchCatalogue();
  return <Storefront products={products} slug={process.env.STOREFRONT_TENANT_SLUG} />;
}
