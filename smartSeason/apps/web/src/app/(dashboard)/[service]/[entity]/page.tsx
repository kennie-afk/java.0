import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, EmptyState, PageHeader, Table, buttonClass, inputClass, rowClass, secondaryButtonClass } from "@/components/ui";
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
  searchParams: Promise<Record<string, string | string[] | undefined>>;
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

  // Search text plus exact-match filters on the entity's own fields. Anything else in the
  // URL is dropped rather than forwarded: the service rejects a field it does not know.
  const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value) ?? "";
  const filterable = new Set(entity.formFields.map((field) => field.name));
  const active: Record<string, string> = {};
  const q = first(query.q).trim().slice(0, 100);
  if (q) active.q = q;
  for (const [key, raw] of Object.entries(query)) {
    if (filterable.has(key) && first(raw)) active[key] = first(raw);
  }
  const filterQuery = new URLSearchParams(active).toString();

  const [{ rows, failed, totalElements, totalPages }, roles] = await Promise.all([
    loadCollection<Record_>(filterQuery ? `${entity.path}?${filterQuery}` : entity.path, page, sort),
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

      <form action={base} className="mb-3 flex flex-wrap items-center gap-2">
        {Object.entries(active)
          .filter(([key]) => key !== "q")
          .map(([key, value]) => (
            <input key={key} type="hidden" name={key} value={value} />
          ))}
        {sort ? <input type="hidden" name="sort" value={sort} /> : null}
        <input
          name="q"
          defaultValue={q}
          placeholder={`Search ${entity.label.toLowerCase()}`}
          aria-label={`Search ${entity.label.toLowerCase()}`}
          className={`${inputClass} max-w-xs`}
        />
        <button type="submit" className={secondaryButtonClass}>
          Search
        </button>
        {Object.keys(active).length > 0 ? (
          <Link href={base} className="text-sm font-semibold text-[var(--color-accent)] hover:underline">
            Clear {Object.keys(active).length === 1 ? "filter" : "filters"}
          </Link>
        ) : null}
      </form>

      {failed ? (
        <EmptyState
          message={`${service.slug}-service is not reachable.`}
          detail="The service is not running, or it rejected the request."
        />
      ) : rows.length === 0 ? (
        <EmptyState
          message={Object.keys(active).length > 0 ? `No ${entity.label.toLowerCase()} match.` : `No ${entity.label.toLowerCase()} yet.`}
          detail={
            Object.keys(active).length > 0
              ? "Try a different search, or clear the filters."
              : mayCreate
              ? `Create the first ${entity.singular.toLowerCase()} to see it here.`
              : "Nothing has been recorded here yet."
          }
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-[var(--color-line)] bg-[var(--color-surface)] shadow-[var(--shadow-card)]">
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
                  extra={active}
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
        extra={active}
      />
    </>
  );
}
