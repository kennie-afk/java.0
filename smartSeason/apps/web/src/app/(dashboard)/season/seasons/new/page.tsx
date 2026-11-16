import Link from "next/link";
import { EmptyState, PageHeader, secondaryButtonClass } from "@/components/ui";
import { api, type PageResponse } from "@/lib/api";
import { canWrite } from "@/lib/roles";
import { readRoles, readToken } from "@/lib/session";
import { StartSeasonForm, type PlotOption } from "./start-season-form";

interface Plot {
  id: string;
  name: string;
  areaHa: number | null;
  currentCrop: string | null;
}

export default async function StartSeasonPage() {
  const roles = await readRoles();
  if (!canWrite(roles, "season")) {
    return (
      <>
        <PageHeader title="Start a season" subtitle="Plan a crop on one of your plots." />
        <EmptyState
          message="You do not have permission to change this."
          detail="Your role can view seasons but not start them. Ask an administrator if that is wrong."
          action={<Link href="/season/seasons" className={secondaryButtonClass}>Back to seasons</Link>}
        />
      </>
    );
  }

  let plots: PlotOption[] = [];
  try {
    const page = await api.get<PageResponse<Plot>>("/api/farm/v1/plots?size=200", await readToken());
    plots = page.content.map((p) => ({
      id: p.id,
      label: `${p.name}${p.areaHa ? ` · ${p.areaHa} ha` : ""}${p.currentCrop ? ` · now ${p.currentCrop.toLowerCase()}` : ""}`
    }));
  } catch {
    // The form still renders; the plot list says there are none.
  }

  return (
    <>
      <PageHeader title="Start a season" subtitle="Choose the plot, the crop and the planting date. The calendar is built for you." />
      <StartSeasonForm plots={plots} />
    </>
  );
}
