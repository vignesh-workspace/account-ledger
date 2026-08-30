package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/**
 * Undoes the effect of an earlier entry by appending its opposite. Nothing is mutated and
 * nothing is removed: after a reversal the journal holds both the original and the
 * correction, and both are visible in any query whose knowledge day is late enough to see
 * them.
 *
 * <p>Carries no amount of its own — it takes the amount of the entry it reverses, which is
 * not known until that entry is located. Carries its own value day, which is normally the
 * original's: reversing a backdated entry has to restate the same day the original affected,
 * or the correction would land somewhere the error never did.
 */
public record Reversal(
        EventId eventId,
        BusinessDay bookingDay,
        AccountId accountId,
        EventId reversedEventId,
        BusinessDay valueDay) implements LedgerEvent {

    public Reversal {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingDay, "bookingDay");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(reversedEventId, "reversedEventId");
        Objects.requireNonNull(valueDay, "valueDay");
        if (eventId.equals(reversedEventId)) {
            throw new IllegalArgumentException("An event cannot reverse itself: " + eventId);
        }
    }

    @Override
    public String describe() {
        return "REVERSAL of " + reversedEventId;
    }
}
