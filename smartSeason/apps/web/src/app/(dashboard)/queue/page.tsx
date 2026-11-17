import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, EmptyState, PageHeader, Table, Tabs } from "@/components/ui";
import { CoverageForm } from "@/components/case-forms";
import { loadCoverage, loadMyCoverage, loadQueue, loadSummary, PRIORITY_LABEL } from "@/lib/cases";
import { canUse } from "@/lib/roles";
import { readRoles } from "@/lib/session";
import { ago } from "@/lib/when";

const SCOPES = [
  { key: "UNASSIGNED", label: "Waiting for someone" },
  { key: "MINE", label: "Mine" },
  { key: "ANSWERED", label: "Answered" }
] as const;

export default async function QueuePage({
  searchParams
}: {
  searchParams: Promise<{ scope?: string; cursor?: string }>;
}) {
  const roles = await readRoles();
  if (!canUse(roles, "case-queue")) notFound();
  const params = await searchParams;
  const scope = SCOPES.find((s) => s.key === params.scope)?.key ?? "MINE";

  const [page, summary, me, everyone] = await Promise.all([
    loadQueue(scope, params.cursor),
    loadSummary(),
    loadMyCoverage(),
    roles.includes("ADMIN") ? loadCoverage() : Promise.resolve([])
  ]);

  const count = (key: string) => (key === "UNASSIGNED" ? summary.unassigned : key === "MINE" ? summary.mine : null);

  return (
    <>
      <PageHeader
        title="Case queue"
        subtitle="Questions from farmers, most urgent first. Take a waiting case, or work the ones assigned to you."
      />
      {summary.overdue > 0 ? (
        <p className="mb-3 text-xs text-[var(--color-danger)]">
          {summary.overdue} {summary.overdue === 1 ? "case is" : "cases are"} past the time allowed for an answer.
        </p>
      ) : null}

      <div className="grid gap-2.5 lg:grid-cols-[1fr_18rem]">
        <section>
          <Tabs
            active={`/queue?scope=${scope}`}
            items={SCOPES.map((s) => ({
              href: `/queue?scope=${s.key}`,
              label: count(s.key) === null ? s.label : `${s.label} (${count(s.key)})`
            }))}
          />
          {page.items.length === 0 ? (
            <EmptyState
              message={scope === "UNASSIGNED" ? "Nothing is waiting." : scope === "MINE" ? "Nothing assigned to you." : "No answered cases."}
              detail={scope === "MINE" ? "New cases for your counties are assigned to you automatically." : undefined}
            />
          ) : (
            <div className="panel overflow-hidden">
              <Table
                head={[
                  { key: "p", label: "Urgency" },
                  { key: "crop", label: "Crop" },
                  { key: "q", label: "Question" },
                  { key: "who", label: "From" },
                  { key: "age", label: "Waiting" }
                ]}
              >
                {page.items.map((c) => (
                  <tr key={c.id}>
                    <td className="whitespace-nowrap">
                      <Badge value={PRIORITY_LABEL[c.priority] ?? "Medium"} />
                    </td>
                    <td className="whitespace-nowrap">
                      {c.cropCode ?? "—"}
                      {c.county ? <span className="text-[var(--color-muted)]"> · {c.county}</span> : null}
                    </td>
                    <td className="max-w-md">
                      <Link href={`/cases/${c.id}`} className={`underline-offset-2 hover:underline ${c.unread ? "font-semibold" : ""}`}>
                        {c.question}
                      </Link>
                      {c.unread ? <span className="ml-2 text-xs font-medium text-[var(--color-accent)]">New</span> : null}
                      {c.aiConfidence !== null ? (
                        <span className="block text-xs text-[var(--color-muted)]">
                          AI {Math.round(c.aiConfidence * 100)}% sure{c.photoCount > 0 ? ` · ${c.photoCount} photo${c.photoCount > 1 ? "s" : ""}` : ""}
                        </span>
                      ) : null}
                    </td>
                    <td className="whitespace-nowrap text-[var(--color-muted)]">{c.raisedByName ?? "—"}</td>
                    <td className="whitespace-nowrap">
                      {ago(c.createdAt)}
                      {c.overdue ? <span className="ml-2 text-xs text-[var(--color-danger)]">Late</span> : null}
                    </td>
                  </tr>
                ))}
              </Table>
            </div>
          )}
          {page.next ? (
            <p className="mt-3 text-xs">
              <Link href={`/queue?scope=${scope}&cursor=${page.next}`} className="underline underline-offset-2">
                Next
              </Link>
            </p>
          ) : null}
        </section>

        <aside className="space-y-2.5">
          {me ? (
            <div className="panel p-3">
              <h2 className="mb-2 text-sm font-semibold">My coverage</h2>
              <CoverageForm counties={me.counties} maxOpen={me.maxOpen} active={me.active} />
              <p className="mt-2 text-xs text-[var(--color-muted)]">
                {me.openCases} open now. Cases are given to whoever covers the county and has the fewest.
              </p>
            </div>
          ) : null}
          {everyone.length > 0 ? (
            <div className="panel p-3">
              <h2 className="mb-2 text-sm font-semibold">Agronomists</h2>
              <ul className="space-y-1.5 text-xs">
                {everyone.map((p) => (
                  <li key={p.userId} className="flex items-baseline justify-between gap-2">
                    <span className={p.active ? "" : "text-[var(--color-faint)]"}>
                      {p.fullName ?? "Unnamed"}
                      <span className="block text-[var(--color-muted)]">
                        {p.counties.length ? p.counties.join(", ") : "every county"}
                      </span>
                    </span>
                    <span className="tabular-nums text-[var(--color-muted)]">
                      {p.openCases}/{p.maxOpen}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </aside>
      </div>
    </>
  );
}
