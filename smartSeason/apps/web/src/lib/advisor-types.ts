/** Shapes shared between the advisor action and the form that calls it. */

export interface Candidate {
  code: string | null;
  commonName: string | null;
  scientificName: string | null;
  confidence: number;
  reasoning: string | null;
}

export interface Diagnosis {
  candidates: Candidate[];
  severity: string;
  management: string;
  immediateAction: string;
  source: string;
  caveats: string[];
}

export interface DiagnosisState {
  result: Diagnosis | null;
  error: string | null;
  /** Echoed back so the form can redisplay what was asked. */
  crop: string;
  symptoms: string;
  imageCount: number;
}

export const emptyDiagnosis: DiagnosisState = {
  result: null,
  error: null,
  crop: "MAIZE",
  symptoms: "",
  imageCount: 0
};
