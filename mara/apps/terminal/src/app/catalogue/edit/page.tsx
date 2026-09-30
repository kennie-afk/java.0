"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { CatalogueForm } from "@/components/catalogue-form";
import { ConfirmDelete, EmptyState, LinkButton, Loading, PageHeader } from "@/components/ui";
import { useTerminalStatus } from "@/components/use-status";
import { deleteItem, getItem } from "@/lib/catalogue-store";
import type { CatalogueItem } from "@/lib/records";

function Edit() {
  const id = useSearchParams().get("id") ?? "";
  const router = useRouter();
  const s = useTerminalStatus();
  const [item, setItem] = useState<CatalogueItem | null | undefined>(undefined);

  useEffect(() => {
    void getItem(id).then(setItem);
  }, [id]);

  if (!s.loaded || item === undefined) return <Loading />;
  if (!item) {
    return (
      <div className="card">
        <EmptyState title="Item not found" action={<LinkButton href="/catalogue">Back to catalogue</LinkButton>}>
          It may have been deleted.
        </EmptyState>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <PageHeader
        title="Edit item"
        sub="Open tabs keep the price they were added at; only new additions use the changed price."
        actions={
          <ConfirmDelete
            label="Delete item"
            what={`"${item.name}"`}
            onConfirm={async () => {
              await deleteItem(item.id);
              router.push("/catalogue");
            }}
          />
        }
      />
      <CatalogueForm currency={s.currency} item={item} />
    </div>
  );
}

export default function EditItemPage() {
  return (
    <Suspense fallback={<Loading />}>
      <Edit />
    </Suspense>
  );
}
