/**
 * The stage calendar shown while starting a season. The season service builds the real
 * stages from the same figures (CropCalendar.java), so this is only a preview of what it
 * will create; the server stays the authority.
 */
export interface Crop {
  code: string;
  name: string;
  stages: { name: string; days: number }[];
  /** Typical rainfed yield in kg per hectare, used to suggest an expected yield. */
  yieldKgPerHa: number;
}

export const CROPS: Crop[] = [
  { code: "MAIZE", name: "Maize", yieldKgPerHa: 2700, stages: [
    { name: "Emergence", days: 10 }, { name: "Vegetative", days: 50 }, { name: "Tasseling", days: 21 },
    { name: "Grain fill", days: 49 }, { name: "Maturity", days: 20 }] },
  { code: "POTATO", name: "Potato", yieldKgPerHa: 20000, stages: [
    { name: "Emergence", days: 14 }, { name: "Vegetative", days: 26 }, { name: "Tuber initiation", days: 20 },
    { name: "Tuber bulking", days: 35 }, { name: "Maturity", days: 15 }] },
  { code: "BEANS", name: "Beans", yieldKgPerHa: 900, stages: [
    { name: "Emergence", days: 7 }, { name: "Vegetative", days: 28 }, { name: "Flowering", days: 15 },
    { name: "Pod fill", days: 25 }, { name: "Maturity", days: 15 }] },
  { code: "TOMATO", name: "Tomato", yieldKgPerHa: 25000, stages: [
    { name: "Emergence", days: 10 }, { name: "Vegetative", days: 30 }, { name: "Flowering", days: 20 },
    { name: "Fruit set", days: 30 }, { name: "Maturity", days: 30 }] },
  { code: "SORGHUM", name: "Sorghum", yieldKgPerHa: 1200, stages: [
    { name: "Emergence", days: 8 }, { name: "Vegetative", days: 35 }, { name: "Flowering", days: 17 },
    { name: "Grain fill", days: 35 }, { name: "Maturity", days: 15 }] }
];

export const totalDays = (crop: Crop) => crop.stages.reduce((sum, stage) => sum + stage.days, 0);

export function addDays(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
