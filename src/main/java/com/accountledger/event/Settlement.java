package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/**
 * Completion of an earlier authorization. The settled amount need not equal the held amount;
 * a merchant may capture less than was reserved.
 *
 * <p>The referenced authorization may not exist. That is not a malformed event and so is not
 * rejected here at construction: it is a business outcome the engine decides on, and it has
 * to reach the engine to be recorded as one.
 */
public record Settlement(
        EventId eventId,
        BusinessDay bookingDay,
        AccountId accountId,
        AuthorizationId authorizationId,
        Money amount,
        BusinessDay valueDay) implements LedgerEvent {

    public Settlement {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingDay, "bookingDay");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authorizationId, "authorizationId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(valueDay, "valueDay");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "A settlement must be for a positive amount, got " + amount);
        }
    }

    @Override
    public String describe() {
        return "SETTLEMENT " + authorizationId + " settles for " + amount;
    }
}
