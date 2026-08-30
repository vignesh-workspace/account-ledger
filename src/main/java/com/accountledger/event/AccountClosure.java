package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/**
 * Closes an account to further entries.
 *
 * <p>An event rather than a method on the registry, so that closing lands in the journal and
 * replays with everything else. Rebuild the ledger from the journal and the account closes
 * again on the same day, for the same reason, in the same order relative to the instructions
 * around it. A closure applied outside the journal would be invisible to that rebuild.
 *
 * <p>There is no deletion event and there will not be one. The entries of a closed account are
 * still true statements about the days it was open, and a statement for one of those days must
 * still be producible afterwards. This is the same principle that makes the criterion about
 * balances returning to their pre-debit values impossible: the past does not become false
 * because the present changed.
 *
 * <p>Carries no value day of its own. No money moves, so there is no day for money to move on;
 * a closure takes effect when it is booked, and {@link #valueDay()} answers with the booking
 * day rather than inventing a second date that nothing would use.
 */
public record AccountClosure(
        EventId eventId,
        BusinessDay bookingDay,
        AccountId accountId) implements LedgerEvent {

    public AccountClosure {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingDay, "bookingDay");
        Objects.requireNonNull(accountId, "accountId");
    }

    @Override
    public BusinessDay valueDay() {
        return bookingDay;
    }

    @Override
    public String describe() {
        return "CLOSE account " + accountId;
    }
}
