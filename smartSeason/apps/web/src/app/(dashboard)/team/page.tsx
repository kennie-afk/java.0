import { EmptyState, PageHeader } from "@/components/ui";
import { TeamManager } from "@/components/team-manager";
import { loadTeam } from "@/lib/team-actions";
import { canUse } from "@/lib/roles";
import { readRoles } from "@/lib/session";

export const dynamic = "force-dynamic";

export default async function TeamPage() {
  const roles = await readRoles();
  const canSee = canUse(roles, "team");
  const canManage = canUse(roles, "team-manage");

  if (!canSee) {
    return (
      <>
        <PageHeader title="Team" />
        <EmptyState
          message="You do not have permission to see the team."
          detail="Ask an administrator if that is wrong."
        />
      </>
    );
  }

  const { members, failed } = await loadTeam();

  return (
    <>
      <PageHeader
        title="Team"
        subtitle={
          canManage
            ? "Everyone in the organisation. A person's roles decide which screens they see and what the services will let them do."
            : "Everyone in the organisation and what they may do."
        }
      />
      {failed ? (
        <EmptyState message="identity-service is not reachable." />
      ) : members.length === 0 ? (
        <EmptyState message="Nobody here yet." />
      ) : (
        <TeamManager members={members} canManage={canManage} />
      )}
    </>
  );
}
