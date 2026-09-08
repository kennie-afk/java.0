/**
 * Who sees what, in the browser.
 *
 * The table itself lives in `tools/rbac.py` and reaches here through
 * `catalogue.generated.ts`, which is the same table the controllers are
 * annotated from. That is the point: the menu cannot offer something the
 * service will refuse, and neither can drift from the other.
 *
 * This module decides *presentation*. The services enforce the same rules on
 * every request; hiding a screen here is a convenience, never a control.
 */
import {
  canDelete as canDeleteService,
  canRead,
  canUseFeature,
  canWrite as canWriteService,
  FEATURE_ROLES,
  GROUPS,
  ROLE_LABELS,
  ROLES,
  type Role
} from "@/lib/catalogue.generated";

export { ROLE_LABELS, ROLES, FEATURE_ROLES };
export type { Role };

/** The strongest role the user holds; an unknown role gets the narrowest view. */
export function primaryRole(roles: string[]): Role {
  const held = roles.map((role) => role.trim().toUpperCase());
  for (const candidate of ROLES) {
    if (held.includes(candidate)) return candidate;
  }
  return "WORKER";
}

export function canSeeGroup(roles: string[], groupSlug: string): boolean {
  const role = primaryRole(roles);
  const group = GROUPS.find((candidate) => candidate.slug === groupSlug);
  return Boolean(group?.services.some((service) => canRead(role, service.slug)));
}

export function canSeeService(roles: string[], serviceSlug: string): boolean {
  return canRead(primaryRole(roles), serviceSlug);
}

export function canWrite(roles: string[], serviceSlug: string): boolean {
  return canWriteService(primaryRole(roles), serviceSlug);
}

export function canDelete(roles: string[], serviceSlug: string): boolean {
  return canDeleteService(primaryRole(roles), serviceSlug);
}

export function canUse(roles: string[], feature: string): boolean {
  return canUseFeature(primaryRole(roles), feature);
}

/** Named wrappers for the features the rail links to. */
export const canSeeAdvisor = (roles: string[]) => canUse(roles, "advisor");
export const canSeeMyWork = (roles: string[]) => canUse(roles, "my-work");
export const canSeeLiveBoard = (roles: string[]) => canUse(roles, "live-board");
export const canReportObservation = (roles: string[]) => canUse(roles, "report-observation");
