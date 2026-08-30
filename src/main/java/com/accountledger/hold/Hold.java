package com.accountledger.hold;

import com.accountledger.account.AccountId;
import com.accountledger.event.AuthorizationId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/**
 * Funds reserved against an account.
 *
 * <p>A hold is not an entry. No money has moved, so nothing is booked and the ledger balance
 * is untouched; only the available balance falls. This is the distinction the whole
 * authorization model rests on, and it is why a hold lives in its own registry rather than in
 * the ledger book.
 */
public record Hold(
        AuthorizationId id,
        AccountId accountId,
        Money amount,
        BusinessDay placedOn) {

    public Hold {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(placedOn, "placedOn");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A hold reserves a positive amount, got " + amount);
        }
    }

    @Override
    public String toString() {
        return id + " " + amount + " placed " + placedOn;
    }
}
