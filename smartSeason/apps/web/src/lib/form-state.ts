export interface FormState {
  ok: boolean;
  message: string | null;
  fieldErrors: Record<string, string>;
}

export const emptyFormState: FormState = { ok: false, message: null, fieldErrors: {} };
