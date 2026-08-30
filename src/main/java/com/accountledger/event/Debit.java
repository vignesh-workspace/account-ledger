package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/** Money out of the account. */
public record Debit(
        EventId eventId,
        BusinessDay bookingDay,
        AccountId accountId,
        Money amount,
        BusinessDay valueDay) implements LedgerEvent {

    public Debit {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingDay, "bookingDay");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(valueDay, "valueDay");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "A debit amount must be positive; direction is carried by the event kind, "
                            + "not by the sign of the amount. Got " + amount);
        }
    }

    @Override
    public String describe() {
        return "DEBIT " + amount;
    }
}
