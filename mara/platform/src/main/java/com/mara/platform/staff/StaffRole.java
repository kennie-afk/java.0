package com.mara.platform.staff;

/**
 * What a member of staff may do.
 *
 * <p>Four roles rather than a permission matrix. A shop floor has four kinds of person
 * and inventing a fifth in software produces roles nobody occupies, which get assigned
 * by accident. If a tenant genuinely needs finer control, that is a reason to add a
 * named role with a stated purpose — not a checkbox grid an owner will misconfigure.
 */
public enum StaffRole {

    /** Sells. May do nothing that reduces the expected takings. */
    CASHIER(0),

    /** Authorises voids, overrides and drawer opens on the floor. */
    SUPERVISOR(1),

    /** Refunds, price changes, stock adjustments. Runs a branch. */
    MANAGER(2),

    /** Everything, including staff and terminals. Spans branches. */
    OWNER(3);

    private final int rank;

    StaffRole(int rank) {
        this.rank = rank;
    }

    public boolean canAuthorise(ElevatedAction action) {
        return rank >= requiredRank(action.sensitivity());
    }

    /**
     * Whether this role may act across every branch of its tenant.
     *
     * <p>Only an owner may. A manager runs one branch, and a manager who could authorise
     * a refund at a branch they have never visited is a manager whose credentials are
     * worth stealing.
     */
    public boolean spansBranches() {
        return this == OWNER;
    }

    private static int requiredRank(ElevatedAction.Sensitivity sensitivity) {
        return switch (sensitivity) {
            case SUPERVISOR -> SUPERVISOR.rank;
            case MANAGER -> MANAGER.rank;
            case OWNER -> OWNER.rank;
        };
    }
}
