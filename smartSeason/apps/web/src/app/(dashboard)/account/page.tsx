import { PageHeader } from "@/components/ui";
import { ChangePasswordForm } from "@/components/change-password-form";
import { ROLE_LABELS, primaryRole } from "@/lib/roles";
import { readRoles } from "@/lib/session";

export default async function AccountPage() {
  const roles = await readRoles();

  return (
    <>
      <PageHeader
        title="Your account"
        subtitle={`Signed in as ${ROLE_LABELS[primaryRole(roles)]}. Changing your password signs out every other session.`}
      />
      <ChangePasswordForm />
    </>
  );
}
