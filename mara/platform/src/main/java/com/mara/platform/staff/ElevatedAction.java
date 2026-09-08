package com.mara.platform.staff;

/**
 * An action that removes money from the day's expected takings, or could.
 *
 * <p>These are enumerated rather than left implicit because they are where retail theft
 * actually happens. A cashier does not steal by taking cash from the drawer — the drawer
 * is counted. They steal by making the record match the shortfall: ring a sale, take the
 * money, void the line before the receipt prints. The till balances and the stock is
 * gone.
 *
 * <p>So every one of these requires an authorising identity distinct from a plain
 * cashier's, carries a mandatory reason, and lands in an append-only audit record. Not
 * because supervisors are honest, but because it makes the action attributable — and an
 * attributable theft is one that can be found.
 */
public enum ElevatedAction {

    /** Remove a line from an open tab before it is tendered. */
    VOID_LINE(Sensitivity.SUPERVISOR),

    /** Abandon an entire open tab. */
    VOID_SALE(Sensitivity.SUPERVISOR),

    /**
     * Reverse a sale that has already been tendered and receipted.
     *
     * <p>Manager rather than supervisor: this one moves money that has already been
     * counted, and it is the classic route for a refund to a card the customer never
     * used.
     */
    REFUND(Sensitivity.MANAGER),

    /** Sell a line at other than its catalogue price. */
    PRICE_OVERRIDE(Sensitivity.SUPERVISOR),

    /** Discount beyond the limit a cashier may apply unaided. */
    DISCOUNT_ABOVE_LIMIT(Sensitivity.SUPERVISOR),

    /**
     * Open the cash drawer without a sale.
     *
     * <p>Trivial-looking and deliberately included. A drawer that opens without a
     * transaction is the simplest theft in retail, and the only defence is that every
     * opening has a name and a reason attached to it.
     */
    NO_SALE_DRAWER(Sensitivity.SUPERVISOR),

    /** Reprint a receipt — a duplicate can be used to support a false refund. */
    REPRINT_RECEIPT(Sensitivity.SUPERVISOR),

    /** Declare the drawer count and close the shift. */
    CLOSE_SHIFT(Sensitivity.SUPERVISOR),

    /** Change what a product costs. */
    EDIT_PRICE(Sensitivity.MANAGER),

    /** Adjust stock on hand outside a sale or a delivery. */
    STOCK_ADJUSTMENT(Sensitivity.MANAGER),

    /** Add, suspend or revoke a member of staff. */
    MANAGE_STAFF(Sensitivity.OWNER),

    /** Issue an enrolment code admitting a new terminal. */
    ENROL_TERMINAL(Sensitivity.OWNER);

    private final Sensitivity sensitivity;

    ElevatedAction(Sensitivity sensitivity) {
        this.sensitivity = sensitivity;
    }

    public Sensitivity sensitivity() {
        return sensitivity;
    }

    /** Every one of these must carry a reason; none may be performed silently. */
    public boolean requiresReason() {
        return true;
    }

    public enum Sensitivity {
        SUPERVISOR,
        MANAGER,
        OWNER
    }
}
