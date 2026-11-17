import { PageHeader } from "@/components/ui";
import { AdvisorForm } from "@/components/advisor-form";
import { canUse } from "@/lib/roles";
import { readRoles } from "@/lib/session";

export default async function AdvisorPage() {
  const roles = await readRoles();
  return (
    <>
      <PageHeader
        title="Crop advisor"
        subtitle="Identify a pest or disease from a description, a photograph, or both. The answer comes from a vision model; if it cannot answer, you can ask an agronomist."
      />
      <AdvisorForm canAsk={canUse(roles, "cases")} />
    </>
  );
}
