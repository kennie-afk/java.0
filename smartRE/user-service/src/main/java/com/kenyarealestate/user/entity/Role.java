package com.kenyarealestate.user.entity;

// AGENT was removed in V11: it conferred nothing SELLER did not already confer, while
// implying a licence check to buyers that never happened.
/**
 * What a person is on this platform.
 *
 * <p>TENANT was added on 2026-09-11. Until then a renting tenant was a BUYER whose
 * relationship happened to be a tenancy, which meant the tenancy endpoints were open to
 * every authenticated account and a tenant carried the sale marketplace they had no use
 * for. The distinction is real — a tenant sees their own lease, invoices and maintenance
 * requests, and does not list or buy property — so it is a role rather than a label.
 *
 * <p>Note the lesson from AGENT, removed in V11: a role that implies a check without
 * performing one is worse than no role. TENANT is enforced in
 * {@code pms SecurityConfig}, not merely displayed.
 */
public enum Role { BUYER, SELLER, LANDLORD, TENANT, ADMIN }
