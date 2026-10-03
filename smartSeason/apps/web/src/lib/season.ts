/**
 * Season arithmetic shared by every screen that shows a crop's progress.
 *
 * Progress here is calendar time against the expected harvest date, not agronomy: a crop
 * can be early or late for its stage, and the stage badge says that. Keeping one copy means
 * the overview and the farm page can never disagree about how late a harvest is.
 */
import type { Record_ } from "@/lib/record";

export interface Season extends Record_ {
  id: string;
  plotId: string;
  farmId: string;
  cropCode: string;
  variety: string | null;
  startDate: string | null;
  expectedHarvestDate: string | null;
  actualHarvestDate: string | null;
  expectedYieldKg: number | null;
  actualYieldKg: number | null;
  currentStage: string | null;
  status: string;
}

const DAY = 86_400_000;

/** Whole days between two calendar dates, ignoring the time of day. */
export function daysBetween(from: Date, to: Date): number {
  const a = Date.UTC(from.getFullYear(), from.getMonth(), from.getDate());
  const b = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate());
  return Math.round((b - a) / DAY);
}

export interface Progress {
  fraction: number;
  label: string;
  tone: "accent" | "danger" | "good";
  /** Days until harvest; negative when overdue, null once harvested or undated. */
  daysLeft: number | null;
}

export function progressOf(season: Season, today: Date): Progress | null {
  if (season.actualHarvestDate) {
    return { fraction: 1, label: `Harvested ${season.actualHarvestDate}`, tone: "good", daysLeft: null };
  }
  if (!season.startDate || !season.expectedHarvestDate) return null;
  const start = new Date(season.startDate);
  const end = new Date(season.expectedHarvestDate);
  const total = daysBetween(start, end);
  if (total <= 0) return null;
  const elapsed = daysBetween(start, today);
  const left = daysBetween(today, end);
  if (left < 0) {
    return { fraction: 1, label: `${-left} day${left === -1 ? "" : "s"} overdue`, tone: "danger", daysLeft: left };
  }
  return {
    fraction: Math.max(0, elapsed / total),
    label: left === 0 ? "Harvest due today" : `${left} day${left === 1 ? "" : "s"} to harvest`,
    tone: "accent",
    daysLeft: left
  };
}

export function kg(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : `${Math.round(value).toLocaleString()} kg`;
}

export function crop(code: string): string {
  return code.charAt(0) + code.slice(1).toLowerCase().replaceAll("_", " ");
}
