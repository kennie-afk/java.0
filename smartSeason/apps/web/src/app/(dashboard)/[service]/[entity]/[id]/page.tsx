import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, EmptyState, PageHeader, buttonClass, secondaryButtonClass } from "@/components/ui";
import { DeleteRecordButton } from "@/components/delete-record-button";
import { findEntity } from "@/lib/catalogue.generated";
import { display, loadRecord } from "@/lib/record";
import { canDelete, canWrite } from "@/lib/roles";
import { readRoles } from "@/lib/session";

export default async function RecordPage({
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
  const [record, roles] = await Promise.all([loadRecord(entity.path, id), readRoles()]);
  const mayEdit = canWrite(roles, service.slug);
  const mayDelete = canDelete(roles, service.slug);

  if (!record) {
    return (
      <>
        <PageHeader title={entity.singular} subtitle={service.description} />
        <EmptyState
          message="This record could not be loaded."
          detail="It may have been deleted, or the service is not reachable."
          action={
            <Link href={base} className={secondaryButtonClass}>
              Back to {entity.label.toLowerCase()}
            </Link>
          }
        />
      </>
    );
  }

  const lead = entity.columns[0];
  const title = display(record[lead.name], lead.kind);

  return (
    <>
      <PageHeader
        title={title === "—" ? entity.singular : title}
        subtitle={`${entity.singular} in ${service.label}`}
        actions={
          <>
            {mayEdit ? (
              <Link href={`${base}/${id}/edit`} className={buttonClass}>
                Edit
              </Link>
            ) : null}
            {mayDelete ? (
              <DeleteRecordButton
                serviceSlug={service.slug}
                entitySlug={entity.slug}
                id={id}
                label={entity.singular.toLowerCase()}
              />
            ) : null}
            <Link href={base} className={secondaryButtonClass}>
              Back
            </Link>
          </>
        }
      />

      <dl className="grid gap-x-8 gap-y-3 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5 sm:grid-cols-2">
        {entity.formFields.map((field) => {
          const value = record[field.name];
          return (
            <div key={field.name}>
              <dt className="text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]">
                {field.label}
              </dt>
              <dd className="mt-0.5 break-words text-xs">
                {field.options && value ? (
                  <Badge value={String(value)} />
                ) : (
                  display(value, field.kind)
                )}
              </dd>
            </div>
          );
        })}
      </dl>
    </>
  );
}
