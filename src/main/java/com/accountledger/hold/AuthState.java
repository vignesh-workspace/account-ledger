package com.accountledger.hold;

/**
 * The lifecycle of an authorization.
 *
 * <p>Only {@link #ACTIVE} reserves funds. Everything else is terminal, and the reasons are
 * kept distinct rather than collapsed into one CLOSED value: an authorization that expired
 * unused and one that settled for the full amount leave the same balance behind but mean
 * entirely different things to whoever has to explain the statement.
 *
 * <p>{@link #EXPIRED} is defined and never reached inside a six-day window with no expiry rule
 * given. It is named here rather than omitted because leaving it out would suggest an
 * authorization can stay live forever, which is the one answer that is certainly wrong.
 */
public enum AuthState {

    /** Holding funds. Reduces available balance, touches no ledger balance. */
    ACTIVE,

    /** A settlement arrived and the hold was consumed. */
    SETTLED,

    /** The authorization was undone before it settled. */
    REVERSED,

    /** The hold aged out. No rule in this window produces it. */
    EXPIRED,

    /** Released without settling: the account closed, or a partial settlement gave back the rest. */
    RELEASED
}
