package com.accountledger.account;

import com.accountledger.time.BusinessDay;
import java.util.Currency;
import java.util.Objects;

/**
 * An account: an identity, a currency and the day it opened.
 *
 * <p><strong>There is deliberately no balance field.</strong> A balance is a pure function of
 * the entries in the ledger book, derived on demand from a value day and a knowledge day. If
 * this record carried a running total, some later method would set it, and append-only would
 * become a convention rather than a structural property. The absence of a setter is the
 * guarantee; a comment saying "do not mutate" would not be.
 *
 * <p>The currency lives here rather than on each event because it is a property of the
 * account for its whole life. An account that could hold two currencies is a different
 * product with a different set of rules, and is named in the cuts rather than half-built.
 */
public record Account(AccountId id, Currency currency, BusinessDay openedOn) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(openedOn, "openedOn");
    }

    @Override
    public String toString() {
        return id + " (" + currency.getCurrencyCode() + ", opened " + openedOn + ")";
    }
}
