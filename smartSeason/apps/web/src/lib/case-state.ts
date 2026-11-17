/** Shared by the case server actions and the forms that call them (a "use server" file may export only functions). */
export interface CaseActionState {
  error: string | null;
  done?: boolean;
}

export const idleCase: CaseActionState = { error: null };
