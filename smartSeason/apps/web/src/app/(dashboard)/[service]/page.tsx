import { notFound, redirect } from "next/navigation";
import { findService } from "@/lib/catalogue.generated";

/** A service on its own has nothing to show; open its first entity. */
export default async function ServicePage({
  params
}: {
  params: Promise<{ service: string }>;
}) {
  const { service: slug } = await params;
  const service = findService(slug);
  if (!service || service.entities.length === 0) {
    notFound();
  }
  redirect(`/${service.slug}/${service.entities[0].slug}`);
}
