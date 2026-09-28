import { api, type PageResponse } from "@/lib/api";
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
 *
 * Requests the largest page a picker should ever need to show (200, the
 * platform's standard list cap) rather than every member: an org large enough
 * to exceed that should get a search box here, not an unbounded fetch.
 */
export async function loadPeople(): Promise<{ id: string; label: string }[]> {
  const token = await readToken();
  try {
    const page = await api.get<PageResponse<Member>>(
      "/api/identity/v1/account/team?size=200",
      token
    );
    return page.content.map((member) => ({
      id: member.id,
      label: `${member.fullName} · ${member.roles.join(", ").toLowerCase()}`
    }));
  } catch {
    return [];
  }
}
