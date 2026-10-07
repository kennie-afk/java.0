import Link from "next/link";
import { redirect } from "next/navigation";
import { ActionForm } from "@/components/action-form";
import { Pager, SearchBar, listQuery, parsePaging, type PagingQuery } from "@/components/pager";
import { api, describeError } from "@/lib/api";
import { readSession } from "@/lib/session";
import { Badge, Notice, PageHeader, Table, buttonClass, dangerButtonClass, rowClass, secondaryButtonClass } from "@/components/ui";
import { setUserStatus } from "../actions";

interface Member { id: string; email: string; fullName: string; role: string; status: string }

export default async function TeamPage({ searchParams }: { searchParams: Promise<PagingQuery> }) {
  // Accounts are the owner's to manage; an operator would only meet a 401, so say so up front.
  const session = await readSession();
  if (session?.role !== "OWNER") redirect("/");

  const { q, page } = parsePaging(await searchParams);
  let members: Member[] = [];
  let total = 0;
  let hasMore = false;
  let error: string | null = null;
  try {
    ({ items: members, total, hasMore } = await api.page<Member>(`/v1/users?${listQuery(q, page)}`));
  } catch (caught) {
    error = describeError(caught);
  }
  if (error) return (<><PageHeader title="Team" /><Notice tone="danger">{error}</Notice></>);

  return (
    <>
      <PageHeader title="Team" subtitle="Everyone who can sign in to this business: staff, supplier logins and customer logins. A suspended account is refused at once."
        actions={<Link href="/team/new" className={buttonClass}>Add account</Link>} />
      <SearchBar q={q} placeholder="Search by name or email" />
      <Table head={["Name", "Email", "Role", "Status", ""]}>
        {members.map((member) => (
          <tr key={member.id} className={rowClass}>
            <td className="px-4 py-3 font-medium">{member.fullName}</td>
            <td className="px-4 py-3 text-[var(--color-muted)]">{member.email}</td>
            <td className="px-4 py-3"><Badge value={member.role} /></td>
            <td className="px-4 py-3"><Badge value={member.status} /></td>
            <td className="px-4 py-3">
              <ActionForm action={setUserStatus} className="flex items-center justify-end gap-2"
                submit={member.status === "ACTIVE" ? "Suspend" : "Reactivate"} pending="Saving…"
                button={member.status === "ACTIVE" ? dangerButtonClass : secondaryButtonClass}>
                <input type="hidden" name="userId" value={member.id} />
                <input type="hidden" name="action" value={member.status === "ACTIVE" ? "suspend" : "reactivate"} />
              </ActionForm>
            </td>
          </tr>
        ))}
      </Table>
      <Pager q={q} page={page} shown={members.length} total={total} hasMore={hasMore} />
    </>
  );
}
