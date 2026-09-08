/** Shapes shared between the team actions and the screens that call them. */

export interface Member {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  roles: string[];
  status: string;
}

export interface TeamState {
  ok: boolean;
  message: string | null;
  /** Echoed back on failure so the form keeps what was typed. */
  values?: Record<string, string>;
}

export const emptyTeamState: TeamState = { ok: false, message: null };
