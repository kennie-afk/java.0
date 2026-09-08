import Link from "next/link";
import { notFound } from "next/navigation";
import { EmptyState, PageHeader, secondaryButtonClass } from "@/components/ui";
import { RecordForm } from "@/components/record-form";
import { findEntity } from "@/lib/catalogue.generated";
import { loadRecord } from "@/lib/record";
import { updateRecord } from "@/lib/record-actions";
import { canWrite } from "@/lib/roles";
import { loadPeople } from "@/lib/people";
import { readRoles } from "@/lib/session";

export default async function EditRecordPage({
  params
}: {
  params: Promise<{ service: string; entity: string; id: string }>;
}) {
  const { service: serviceSlug, entity: entitySlug, id } = await params;
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
        <PageHeader title={`Edit ${entity.singular.toLowerCase()}`} subtitle={service.description} />
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
  const record = await loadRecord(entity.path, id);

  if (!record) {
    return (
      <>
        <PageHeader title={`Edit ${entity.singular.toLowerCase()}`} />
        <EmptyState
          message="This record could not be loaded."
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
        title={`Edit ${entity.singular.toLowerCase()}`}
        subtitle={`Changes are saved to ${service.label}.`}
      />
      <RecordForm
        people={people}
        action={updateRecord.bind(null, service.slug, entity.slug, id)}
        fields={entity.formFields}
        values={record}
        submitLabel="Save changes"
        cancelHref={`${base}/${id}`}
      />
    </>
  );
}
