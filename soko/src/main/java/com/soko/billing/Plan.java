package com.soko.billing;

/**
 * What Soko-the-platform charges a distributor tenant, as opposed to what a
 * distributor charges their own customers (that's {@code SalesOrder.marginCents},
 * untouched by this). Every number here is a placeholder Kennedy should set
 * with real pricing before this goes anywhere near a paying tenant -- there is
 * no market research behind 3.50% or KSh 2,999, only a plausible SaaS shape
 * (falling take rate as volume/commitment rises, the standard model for a
 * marketplace that also charges a platform fee).
 */
public enum Plan {
    FREE(0L, 500),
    GROWTH(299_900L, 350),
    SCALE(999_900L, 200);

    private final long monthlyFeeCents;
    private final int commissionBps;

    Plan(long monthlyFeeCents, int commissionBps) {
        this.monthlyFeeCents = monthlyFeeCents;
        this.commissionBps = commissionBps;
    }

    public long monthlyFeeCents() { return monthlyFeeCents; }
    public int commissionBps() { return commissionBps; }

    public long commissionOn(long revenueCents) {
        return Math.floorDiv(revenueCents * commissionBps, 10_000L);
    }
}
