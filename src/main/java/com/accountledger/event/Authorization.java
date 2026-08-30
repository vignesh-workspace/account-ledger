package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/**
 * A request to reserve funds. If approved it places a hold, which reduces available balance
 * without touching the ledger balance: no entry is booked, because no money has moved.
 */
public record Authorization(
        EventId eventId,
        BusinessDay bookingDay,
        AccountId accountId,
        AuthorizationId authorizationId,
        Money amount,
        BusinessDay valueDay) implements LedgerEvent {

    public Authorization {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingDay, "bookingDay");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authorizationId, "authorizationId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(valueDay, "valueDay");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A hold must be for a positive amount, got " + amount);
        }
    }

    @Override
    public String describe() {
        return "AUTHORIZATION " + authorizationId + " hold " + amount;
    }
}
