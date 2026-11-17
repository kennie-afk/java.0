import Link from "next/link";
import { Badge, EmptyState, PageHeader, Table, buttonClass } from "@/components/ui";
import { loadMine, PRIORITY_LABEL } from "@/lib/cases";
import { canUse, primaryRole } from "@/lib/roles";
import { readRoles } from "@/lib/session";
import { ago } from "@/lib/when";

export default async function CasesPage({
  searchParams
}: {
  searchParams: Promise<{ scope?: string; cursor?: string }>;
}) {
  const { scope, cursor } = await searchParams;
  const roles = await readRoles();
  const role = primaryRole(roles);
  const mayWiden = role === "FARMER" || role === "ADMIN";
  const whole = mayWiden && scope === "org";
  const page = await loadMine(whole, cursor);

  return (
    <>
      <PageHeader
        title={whole ? "All questions in your organisation" : "My questions"}
        subtitle="Questions you sent to an agronomist, and their answers."
        actions={
          <>
            {mayWiden ? (
              <Link href={whole ? "/cases" : "/cases?scope=org"} className="text-xs underline underline-offset-2">
                {whole ? "Only mine" : "Whole organisation"}
              </Link>
            ) : null}
            {canUse(roles, "advisor") ? (
              <Link href="/advisor" className={buttonClass}>
                New question
              </Link>
            ) : null}
          </>
        }
      />

      {page.items.length === 0 ? (
        <EmptyState
          message="No questions yet."
          detail="Describe a problem in the Crop advisor. If you want a person to look at it, press Ask an agronomist."
        />
      ) : (
        <div className="panel overflow-hidden">
          <Table
            head={[
              { key: "crop", label: "Crop" },
              { key: "q", label: "Question" },
              { key: "status", label: "Status" },
              { key: "with", label: "Agronomist" },
              { key: "age", label: "Asked" }
            ]}
          >
            {page.items.map((c) => (
              <tr key={c.id}>
                <td className="whitespace-nowrap">
                  {c.cropCode ?? "—"}
                  {c.county ? <span className="text-[var(--color-muted)]"> · {c.county}</span> : null}
                </td>
                <td className="max-w-md">
                  <Link href={`/cases/${c.id}`} className={`underline-offset-2 hover:underline ${c.unread ? "font-semibold" : ""}`}>
                    {c.question}
                  </Link>
                  {c.unread ? <span className="ml-2 text-xs font-medium text-[var(--color-accent)]">New reply</span> : null}
                  {whole && c.raisedByName ? (
                    <span className="block text-xs text-[var(--color-muted)]">{c.raisedByName}</span>
                  ) : null}
                </td>
                <td>
                  <Badge value={c.status} />
                  {c.overdue ? <span className="ml-2 text-xs text-[var(--color-danger)]">Late</span> : null}
                </td>
                <td className="whitespace-nowrap text-[var(--color-muted)]">
                  {c.assignedToName ?? (c.status === "OPEN" ? "Waiting" : "—")}
                </td>
                <td className="whitespace-nowrap text-[var(--color-muted)]">
                  {ago(c.createdAt)}
                  <span className="ml-2 text-xs">{PRIORITY_LABEL[c.priority]}</span>
                </td>
              </tr>
            ))}
          </Table>
        </div>
      )}
      {page.next ? (
        <p className="mt-3 text-xs">
          <Link href={`/cases?${whole ? "scope=org&" : ""}cursor=${page.next}`} className="underline underline-offset-2">
            Older questions
          </Link>
        </p>
      ) : null}
    </>
  );
}
