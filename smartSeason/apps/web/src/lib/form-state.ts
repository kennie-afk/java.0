export interface FormState {
  ok: boolean;
  message: string | null;
  fieldErrors: Record<string, string>;
  /**
   * What the user submitted, echoed back when the save fails so the form can
   * redisplay their input instead of resetting to the original record.
   */
  values?: Record<string, string>;
}

export const emptyFormState: FormState = { ok: false, message: null, fieldErrors: {} };
