import { createItem, DuplicateSkuError } from "./catalogue-store";

/**
 * A demonstration catalogue of everyday Kenyan kiosk and small-supermarket goods. PRICES AND
 * TAX RATES ARE ILLUSTRATIVE, not a price list and not tax advice: the 0% lines are
 * unprocessed staples commonly treated as exempt or zero-rated and the rest carry the 16%
 * standard VAT rate, but an operator must set the real classification for each product from
 * the VAT Act schedules before trading. Prices are in whole shillings and tax-exclusive, as
 * this till prices.
 */
export const DEMO_ITEMS: ReadonlyArray<{ sku: string; name: string; kes: number; taxBp: number }> = [
  { sku: "5000001", name: "Maize flour 2kg", kes: 175, taxBp: 0 },
  { sku: "5000002", name: "Wheat flour 2kg", kes: 210, taxBp: 0 },
  { sku: "5000003", name: "Sugar 1kg", kes: 160, taxBp: 1600 },
  { sku: "5000004", name: "Cooking oil 1L", kes: 320, taxBp: 1600 },
  { sku: "5000005", name: "Rice pishori 1kg", kes: 230, taxBp: 1600 },
  { sku: "5000006", name: "Fresh milk 500ml", kes: 65, taxBp: 0 },
  { sku: "5000007", name: "Bread white 400g", kes: 65, taxBp: 0 },
  { sku: "5000008", name: "Eggs tray of 30", kes: 480, taxBp: 0 },
  { sku: "5000009", name: "Tea leaves 250g", kes: 120, taxBp: 1600 },
  { sku: "5000010", name: "Salt 1kg", kes: 45, taxBp: 1600 },
  { sku: "5000011", name: "Tomatoes 1kg", kes: 90, taxBp: 0 },
  { sku: "5000012", name: "Onions 1kg", kes: 100, taxBp: 0 },
  { sku: "5000013", name: "Sukuma wiki bunch", kes: 20, taxBp: 0 },
  { sku: "5000014", name: "Potatoes 1kg", kes: 80, taxBp: 0 },
  { sku: "5000015", name: "Bar soap 800g", kes: 185, taxBp: 1600 },
  { sku: "5000016", name: "Washing powder 1kg", kes: 240, taxBp: 1600 },
  { sku: "5000017", name: "Toilet paper 4 roll", kes: 150, taxBp: 1600 },
  { sku: "5000018", name: "Toothpaste 100ml", kes: 170, taxBp: 1600 },
  { sku: "5000019", name: "Petroleum jelly 250ml", kes: 210, taxBp: 1600 },
  { sku: "5000020", name: "Matches box", kes: 10, taxBp: 1600 },
  { sku: "5000021", name: "Soda 500ml", kes: 70, taxBp: 1600 },
  { sku: "5000022", name: "Bottled water 500ml", kes: 40, taxBp: 1600 },
  { sku: "5000023", name: "Juice 1L", kes: 190, taxBp: 1600 },
  { sku: "5000024", name: "Biscuits pack", kes: 50, taxBp: 1600 },
  { sku: "5000025", name: "Chocolate bar", kes: 80, taxBp: 1600 },
  { sku: "5000026", name: "Mandazi (each)", kes: 10, taxBp: 0 },
  { sku: "5000027", name: "Samosa (each)", kes: 30, taxBp: 1600 },
  { sku: "5000028", name: "Airtime voucher 100", kes: 100, taxBp: 0 },
  { sku: "5000029", name: "Exercise book 96pg", kes: 60, taxBp: 1600 },
  { sku: "5000030", name: "Ballpoint pen blue", kes: 20, taxBp: 1600 }
];

export interface DemoLoadResult {
  added: number;
  skipped: number;
}

/** Idempotent: items whose SKU already exists are skipped, never overwritten. */
export async function loadDemoCatalogue(): Promise<DemoLoadResult> {
  let added = 0;
  let skipped = 0;
  for (const item of DEMO_ITEMS) {
    try {
      await createItem({ sku: item.sku, name: item.name, unitMinor: BigInt(item.kes) * 100n, taxBp: item.taxBp });
      added++;
    } catch (e) {
      if (e instanceof DuplicateSkuError) skipped++;
      else throw e;
    }
  }
  return { added, skipped };
}
