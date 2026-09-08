import type { AuthResponse, Role } from '@/types'

type MaybeUser = AuthResponse | null | undefined

// AGENT was removed: it granted nothing SELLER did not already grant, while implying a
// licence check to buyers that never happened. See user-service migration V11.
export const SELLER_ROLES: Role[] = ['SELLER']
export const LANDLORD_ROLES: Role[] = ['LANDLORD']

/**
 * Roles that may create and manage a property listing.
 *
 * Named for the capability rather than the job title, because SELLER_ROLES was being
 * used for both "sells houses" and "may add a property" — and a landlord does the
 * second without doing the first. Guarding property creation on SELLER_ROLES left
 * landlords unable to add a property, and therefore unable to add a unit, since every
 * unit requires a propertyId. The backend always allowed it:
 * PropertyController @PreAuthorize("hasAnyRole('SELLER','LANDLORD')").
 */
export const PROPERTY_OWNER_ROLES: Role[] = ['SELLER', 'LANDLORD']

export const isBuyer = (u: MaybeUser) => u?.role === 'BUYER'
export const isSeller = (u: MaybeUser) => !!u && SELLER_ROLES.includes(u.role)
export const isLandlord = (u: MaybeUser) => !!u && LANDLORD_ROLES.includes(u.role)
export const isAdmin = (u: MaybeUser) => u?.role === 'ADMIN'

/** @deprecated AGENT no longer exists; use {@link isSeller}. Kept so existing call
 *  sites keep compiling while they are renamed. */
export const isSellerOrAgent = isSeller
