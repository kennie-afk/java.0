import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, EmptyState, PageHeader, Table, buttonClass, rowClass } from "@/components/ui";
import { SortLink } from "@/components/sortable-header";
import { Pager } from "@/components/pager";
import { findEntity } from "@/lib/catalogue.generated";
import { loadCollection, pageParam, sortParam } from "@/lib/load";
import { canWrite } from "@/lib/roles";
import { readRoles } from "@/lib/session";
import { display, type Record_ } from "@/lib/record";

export default async function EntityListPage({
  params,
  searchParams
}: {
  params: Promise<{ service: string; entity: string }>;
  searchParams: Promise<{ page?: string; sort?: string }>;
}) {
  const { service: serviceSlug, entity: entitySlug } = await params;
  const found = findEntity(serviceSlug, entitySlug);
  if (!found) {
    notFound();
  }
  const { service, entity } = found;

  const query = await searchParams;
  const page = pageParam(query.page);
  const base = `/${service.slug}/${entity.slug}`;
  const sort = sortParam(
    query.sort,
    entity.columns.map((column) => column.name)
  );

  const [{ rows, failed, totalElements, totalPages }, roles] = await Promise.all([
    loadCollection<Record_>(entity.path, page, sort),
    readRoles()
  ]);
  const mayCreate = canWrite(roles, service.slug);

  return (
    <>
      <PageHeader
        title={entity.label}
        subtitle={service.description}
        actions={
          mayCreate ? (
            <Link href={`${base}/new`} className={buttonClass}>
              New {entity.singular.toLowerCase()}
            </Link>
          ) : null
        }
      />

      {failed ? (
        <EmptyState
          message={`${service.slug}-service is not reachable.`}
          detail="The service is not running, or it rejected the request."
        />
      ) : rows.length === 0 ? (
        <EmptyState
          message={`No ${entity.label.toLowerCase()} yet.`}
          detail={
            mayCreate
              ? `Create the first ${entity.singular.toLowerCase()} to see it here.`
              : "Nothing has been recorded here yet."
          }
        />
      ) : (
        <div className="overflow-hidden rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]">
          <Table
            head={entity.columns.map((column) => ({
              key: column.name,
              numeric: column.numeric,
              label: (
                <SortLink
                  basePath={base}
                  field={column.name}
                  label={column.label}
                  activeSort={sort}
                  numeric={column.numeric}
                />
              )
            }))}
          >
            {rows.map((row) => (
              <tr key={String(row.id)} className={rowClass}>
                {entity.columns.map((column, index) => {
                  const value = row[column.name];
                  return (
                    <td
                      key={column.name}
                      className={`px-3 py-1.5 ${
                        column.numeric ? "tabular-nums text-right" : ""
                      }`}
                    >
                      {index === 0 ? (
                        <Link
                          href={`${base}/${String(row.id)}`}
                          className="font-medium text-[var(--color-ink)] underline-offset-2 hover:underline"
                        >
                          {display(value, column.kind)}
                        </Link>
                      ) : column.badge && value ? (
                        <Badge value={String(value)} />
                      ) : (
                        <span className={index > 0 ? "text-[var(--color-muted)]" : ""}>
                          {display(value, column.kind)}
                        </span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </Table>
        </div>
      )}

      <Pager
        basePath={base}
        page={page}
        totalElements={totalElements}
        totalPages={totalPages}
        sort={sort}
      />
    </>
  );
}
