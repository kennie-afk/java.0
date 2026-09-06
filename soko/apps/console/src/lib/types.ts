export interface Overview {
  orders: number;
  suppliers: number;
  products: number;
  customers: number;
  revenueCents: number;
  marginCents: number;
  marginPercent: number;
}

export interface OrderRow {
  id: string;
  reference: string;
  customer: string | null;
  county: string | null;
  status: string;
  revenueCents: number;
  costCents: number;
  marginCents: number;
  placedAt: string;
}

export interface OfferRow {
  id: string;
  supplier: string | null;
  coldChain: boolean;
  leadTimeHours: number | null;
  product: string | null;
  costCents: number;
  listPriceCents: number | null;
  availableQty: number;
  status: string;
}

export interface ProductRow {
  id: string;
  sku: string;
  name: string;
  category: string;
  unit: string;
  perishable: boolean;
  requiresColdChain: boolean;
  shelfLifeHours: number;
  listPriceCents: number;
}

export interface SupplierRow {
  id: string;
  name: string;
  county: string;
  leadTimeHours: number;
  coldChain: boolean;
  reliability: number;
  status: string;
}
