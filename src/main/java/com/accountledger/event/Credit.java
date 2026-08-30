package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/** Money into the account. */
public record Credit(
        EventId eventId,
        BusinessDay bookingDay,
        AccountId accountId,
        Money amount,
        BusinessDay valueDay) implements LedgerEvent {

    public Credit {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingDay, "bookingDay");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(valueDay, "valueDay");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "A credit must be positive; direction is carried by the event kind, "
                            + "not by the sign of the amount. Got " + amount);
        }
    }

    @Override
    public String describe() {
        return "CREDIT " + amount;
    }
}
