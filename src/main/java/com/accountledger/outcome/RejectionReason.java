package com.accountledger.outcome;

import java.util.Locale;

/**
 * Why an instruction was refused.
 *
 * <p>An enum rather than a string, so a rejection can be counted, filtered and asserted on
 * without matching prose, and so a new reason cannot be introduced by a typo at a call site.
 * Each constant carries the sentence it prints; keeping the wording next to the constant is
 * what stops the same refusal being described three different ways in three different places.
 *
 * <p>Formatting is pinned to {@link Locale#ROOT}. A machine with a different default locale
 * must not produce a different report, and the determinism test compares reports for equality.
 */
public enum RejectionReason {

    UNKNOWN_ACCOUNT("account %s was never opened"),

    ACCOUNT_CLOSED("account %s closed on %s and takes no further entries"),

    ACCOUNT_NOT_YET_OPEN("account %s does not open until %s"),

    ACCOUNT_ALREADY_CLOSED("account %s had already closed on %s"),

    DAY_OUTSIDE_WINDOW("booking day %s falls outside the replay window %s to %s"),

    DUPLICATE_EVENT_ID("event id %s has already been seen in this stream"),

    UNKNOWN_AUTHORIZATION("authorization %s was never approved, so there is nothing to settle"),

    DUPLICATE_AUTHORIZATION_ID("authorization %s has already been approved"),

    AUTHORIZATION_ALREADY_CLOSED("authorization %s is already %s"),

    INSUFFICIENT_AVAILABLE_BALANCE(
            "available balance %s would become %s after a hold of %s"),

    UNKNOWN_ENTRY("event %s booked no entry, so there is nothing to reverse"),

    ALREADY_REVERSED("entry #%s has already been reversed"),

    LATE_ARRIVAL_TO_CLOSED_DAY("booking day %s has already closed; the ledger is now on %s");

    private final String template;

    RejectionReason(String template) {
        this.template = template;
    }

    public String format(Object... arguments) {
        return String.format(Locale.ROOT, template, arguments);
    }
}
