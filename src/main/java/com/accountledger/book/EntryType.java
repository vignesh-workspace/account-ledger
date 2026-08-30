package com.accountledger.book;

/**
 * Why an entry exists, and with it the direction the money moved.
 *
 * <p>Direction belongs to the type rather than to the sign of the amount, for the same reason
 * it belongs to the event kind: a stored negative would admit "a fee of minus twenty-five"
 * and force every call site to remember a sign convention. Amounts are positive everywhere
 * and {@link LedgerEntry#signedAmount()} is the only place the convention is applied.
 *
 * <p>There is no REVERSAL type. A reversal of a debit <em>is</em> a credit — the money really
 * does come back — and giving it its own type would leave the direction undecidable without
 * looking at the entry it corrects. That a credit happens to be a correction is recorded as a
 * link to the original, which is metadata about the entry rather than a different kind of
 * money movement.
 */
public enum EntryType {

    /** The balance an account starts with. Permitted to be zero, unlike every other entry. */
    OPENING(1),

    CREDIT(1),

    DEBIT(-1),

    /** An overdraft fee assessed by the day-close, not by any submitted instruction. */
    FEE(-1),

    /** Capitalised interest, published once at the end of the window. */
    INTEREST(1);

    private final int signum;

    EntryType(int signum) {
        this.signum = signum;
    }

    public int signum() {
        return signum;
    }
}
