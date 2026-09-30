"use client";

import { CatalogueForm } from "@/components/catalogue-form";
import { Loading, PageHeader } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";

export default function NewItemPage() {
  const s = useTerminalStatus();
  if (!s.loaded) return <Loading />;
  return (
    <div className="space-y-3">
      <PageHeader title="Add item" />
      <CatalogueForm currency={s.currency} />
    </div>
  );
}
