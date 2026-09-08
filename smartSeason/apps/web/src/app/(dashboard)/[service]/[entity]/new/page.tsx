import { notFound } from "next/navigation";
import Link from "next/link";
import { EmptyState, PageHeader, secondaryButtonClass } from "@/components/ui";
import { RecordForm } from "@/components/record-form";
import { findEntity } from "@/lib/catalogue.generated";
import { createRecord } from "@/lib/record-actions";
import { canWrite } from "@/lib/roles";
import { loadPeople } from "@/lib/people";
import { readRoles } from "@/lib/session";

export default async function NewRecordPage({
  params
}: {
  params: Promise<{ service: string; entity: string }>;
}) {
  const { service: serviceSlug, entity: entitySlug } = await params;
  const found = findEntity(serviceSlug, entitySlug);
  if (!found) {
    notFound();
  }
  const { service, entity } = found;
  const base = `/${service.slug}/${entity.slug}`;

  const roles = await readRoles();
  if (!canWrite(roles, service.slug)) {
    return (
      <>
        <PageHeader title={`New ${entity.singular.toLowerCase()}`} subtitle={service.description} />
        <EmptyState
          message="You do not have permission to change this."
          detail={`Your role can view ${entity.label.toLowerCase()} but not change them. Ask an administrator if that is wrong.`}
          action={
            <Link href={base} className={secondaryButtonClass}>
              Back to {entity.label.toLowerCase()}
            </Link>
          }
        />
      </>
    );
  }

  const people = entity.formFields.some((field) => field.lookup === "user")
    ? await loadPeople()
    : [];

  return (
    <>
      <PageHeader
        title={`New ${entity.singular.toLowerCase()}`}
        subtitle={`Added to ${entity.label.toLowerCase()} in ${service.label}.`}
      />
      <RecordForm
        people={people}
        action={createRecord.bind(null, service.slug, entity.slug)}
        fields={entity.formFields}
        submitLabel={`Create ${entity.singular.toLowerCase()}`}
        cancelHref={base}
      />
    </>
  );
}
