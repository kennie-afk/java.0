import { Storefront, type PublicProduct } from "@/app/welcome/storefront";

// Forced dynamic on purpose: this page reads STOREFRONT_TENANT_SLUG and calls
// the live API at request time. Without this, Next.js prerenders it once as
// static HTML during the Docker build -- before docker-compose's runtime
// environment even exists and before there is any data to fetch -- and then
// serves that same stale, empty-catalogue page forever regardless of what
// actually changes in the running system.
export const dynamic = "force-dynamic";

async function fetchCatalogue(): Promise<PublicProduct[]> {
  const api = process.env.SOKO_API_URL ?? "http://127.0.0.1:8090";
  const slug = process.env.STOREFRONT_TENANT_SLUG;
  if (!slug) {
    return [];
  }
  try {
    const response = await fetch(`${api}/v1/public/${slug}/products`, { cache: "no-store" });
    if (!response.ok) {
      return [];
    }
    return (await response.json()) as PublicProduct[];
  } catch {
    return [];
  }
}

export default async function WelcomePage() {
  const products = await fetchCatalogue();
  return <Storefront products={products} />;
}
