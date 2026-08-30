package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;

/**
 * An instruction submitted to the ledger. Events are inputs, not results: an event that the
 * engine refuses still exists in the journal and still appears in the day's report. Nothing
 * here records whether the instruction was carried out.
 *
 * <p>Sealed, so dispatch is an exhaustive {@code switch} and adding another kind is a compile
 * error at every handler rather than a silently unhandled case at runtime. This is what
 * stands in for a visitor: the same completeness guarantee without the double dispatch.
 *
 * <p>Two properties every event carries, and the distinction between them is the whole
 * difficulty of the system:
 * <ul>
 *   <li>{@link #bookingDay()} — when the instruction reached the ledger. Determines which
 *       day processes it, and therefore what balance the decision is made against.</li>
 *   <li>{@link #valueDay()} — the day the money is treated as having moved. Determines which
 *       balances the resulting entry contributes to. May be earlier than the booking day,
 *       which is how a balance already reported can later be restated.</li>
 * </ul>
 *
 * <p>No sequence number here. Ordering is assigned at ingest by the replayer, because
 * position in the stream is a property of submission rather than of the instruction.
 */
public sealed interface LedgerEvent
        permits Credit, Debit, Authorization, Settlement, Reversal, AccountClosure {

    EventId eventId();

    /** The day the instruction reached the ledger. */
    BusinessDay bookingDay();

    AccountId accountId();

    /** The day the money is treated as having moved. */
    BusinessDay valueDay();

    /**
     * The amount the instruction concerns, or null where the concept does not apply.
     * A reversal has no amount of its own: it takes the amount of whatever it reverses,
     * which is not known until the original entry is located.
     */
    default Money amount() {
        return null;
    }

    /** Short form used in the day report. */
    String describe();
}
