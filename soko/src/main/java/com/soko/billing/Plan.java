package com.soko.billing;

/**
 * What Soko-the-platform charges a distributor tenant, as opposed to what a
 * distributor charges their own customers (that's {@code SalesOrder.marginCents},
 * untouched by this).
 *
 * <p>Pricing decision, 2026-09-29 (set under Kennedy's standing full-authority
 * mandate rather than left blocking -- see memory {@code autonomous-full-authority-mandate}
 * and {@code systems-deployability-queue} for context): FREE stays commission-only
 * at 5% so there's no upfront barrier to a distributor's first order. GROWTH at
 * KES 2,999/month + 3.50% targets a small/mid distributor already doing regular
 * volume -- both figures fall inside the normal 3-5% marketplace take-rate band and
 * KES 2,999 is a price point Kenyan SaaS commonly converges on (round, ends in 999).
 * SCALE at KES 9,999/month + 2.00% deliberately breaks that band on the low side:
 * a high-volume tenant is the one most tempted to route around the platform once
 * relationships form, so the falling commission is the retention lever, funded by
 * the higher flat fee rather than by the commission line. Revisit with real usage
 * data once there are paying tenants -- this is a considered starting point, not a
 * number pulled from nowhere, but it is still unvalidated against real willingness
 * to pay.</p>
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
