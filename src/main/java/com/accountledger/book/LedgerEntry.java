package com.accountledger.book;

import com.accountledger.account.AccountId;
import com.accountledger.event.EventId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One movement of money, written once and never altered.
 *
 * <p>Carries two days, and the distinction between them is what makes a balance query
 * meaningful. {@code valueDay} says which day the money belongs to; {@code bookingDay} says
 * from which day the ledger knew about it. An entry with a value day earlier than its booking
 * day restates a day that has already been reported, which is exactly what happens when a
 * backdated instruction arrives.
 *
 * <p>{@code sequence} is global and gapless from 1, assigned on append. Per-day numbering was
 * considered and rejected: entries arrive out of booking-day order, so ordering has to be
 * total across the whole book rather than only within a day.
 *
 * <p>{@code reversesSequence} is null for an ordinary entry and otherwise names the entry this
 * one corrects. Nothing is mutated on the original — the link points backwards, so the
 * original is exactly the bytes it always was, and "has it been reversed" is a question the
 * book answers by looking forward.
 */
public record LedgerEntry(
        long sequence,
        EventId sourceEventId,
        AccountId accountId,
        EntryType type,
        Money amount,
        BusinessDay valueDay,
        BusinessDay bookingDay,
        Long reversesSequence) {

    public LedgerEntry {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(valueDay, "valueDay");
        Objects.requireNonNull(bookingDay, "bookingDay");
        if (amount.isNegative()) {
            throw new IllegalArgumentException(
                    "Entry amounts are positive; direction comes from the type. Got " + amount);
        }
        if (amount.isZero() && type != EntryType.OPENING) {
            throw new IllegalArgumentException(
                    "A " + type + " entry of zero moves nothing and would still print as a "
                            + "movement in the day report. Got " + amount);
        }
    }

    /** The amount as it contributes to a balance: positive in, negative out. */
    public BigDecimal signedAmount() {
        return type.signum() < 0 ? amount.amount().negate() : amount.amount();
    }

    public boolean isCorrection() {
        return reversesSequence != null;
    }

    @Override
    public String toString() {
        return "#" + sequence + " " + type + " " + amount
                + " value " + valueDay + " booked " + bookingDay
                + " (" + sourceEventId + ")";
    }
}
