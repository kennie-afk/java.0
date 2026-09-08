import { PageHeader } from "@/components/ui";
import { AdvisorForm } from "@/components/advisor-form";

export default function AdvisorPage() {
  return (
    <>
      <PageHeader
        title="Crop advisor"
        subtitle="Identify a pest or disease from a description, a photograph, or both. Every answer says whether it came from a vision model or the built-in rules."
      />
      <AdvisorForm />
    </>
  );
}
