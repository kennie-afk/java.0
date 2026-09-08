import { api } from "@/lib/api";
import { readToken } from "@/lib/session";

interface Member {
  id: string;
  fullName: string;
  email: string;
  roles: string[];
}

/**
 * People in this organisation, for fields that name one.
 *
 * Returns an empty list rather than failing when the caller may not read the
 * team: the field then shows "Nobody on the team yet" and stays usable for
 * everything else on the form.
 */
export async function loadPeople(): Promise<{ id: string; label: string }[]> {
  const token = await readToken();
  try {
    const members = await api.get<Member[]>("/api/identity/v1/account/team", token);
    return members.map((member) => ({
      id: member.id,
      label: `${member.fullName} · ${member.roles.join(", ").toLowerCase()}`
    }));
  } catch {
    return [];
  }
}
