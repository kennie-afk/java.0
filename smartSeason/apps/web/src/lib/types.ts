export interface Farm {
  id: string;
  name: string;
  ownerUserId: string | null;
  county: string | null;
  subCounty: string | null;
  ward: string | null;
  latitude: number | null;
  longitude: number | null;
  totalAreaHa: number | null;
  status: "ACTIVE" | "ARCHIVED";
  cooperativeId: string | null;
  registrationNo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Plot {
  id: string;
  farmId: string;
  name: string;
  areaHa: number;
  irrigated: boolean;
  currentCrop: string | null;
  status: "ACTIVE" | "FALLOW" | "RETIRED";
}

export interface Season {
  id: string;
  plotId: string;
  farmId: string | null;
  cropCode: string;
  variety: string | null;
  startDate: string;
  expectedHarvestDate: string | null;
  expectedYieldKg: number | null;
  actualYieldKg: number | null;
  currentStage: string | null;
  status: "PLANNED" | "ACTIVE" | "HARVESTED" | "CLOSED" | "ABANDONED";
}

export interface FraudCase {
  id: string;
  caseNumber: string;
  subjectType: "WORKER" | "SUPERVISOR" | "VEHICLE" | "VENDOR" | "BATCH";
  subjectId: string;
  farmId: string | null;
  typology: string;
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  confidence: number;
  openedAt: string;
  status: "OPEN" | "INVESTIGATING" | "CONFIRMED" | "DISMISSED" | "APPEALED" | "CLOSED";
  payoutHeld: boolean;
  resolution: string | null;
}

export interface SupplyListing {
  id: string;
  sellerOrgId: string;
  commodityCode: string;
  variety: string | null;
  grade: string | null;
  quantity: number;
  unit: string;
  askPrice: number;
  currency: string;
  county: string | null;
  status: "DRAFT" | "ACTIVE" | "RESERVED" | "SOLD" | "EXPIRED" | "WITHDRAWN";
}

export interface Worker {
  id: string;
  fullName: string;
  phone: string | null;
  farmId: string | null;
  status: "ACTIVE" | "SUSPENDED" | "TERMINATED";
  riskScore: number;
}
