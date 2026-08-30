package com.accountledger.report;

import com.accountledger.account.AccountId;
import com.accountledger.account.AccountState;
import com.accountledger.money.Money;

/**
 * Where an account finished the window.
 *
 * <p>{@code closingBeforeCapitalisation} is the last day's closing balance as reported, and
 * {@code finalBalance} is that plus the interest credit. The two differ by exactly
 * {@code capitalisedInterest}, and keeping all three visible is what settles the question of
 * whether capitalisation is inside the final day's closing balance or applied after it. It is
 * applied after.
 */
public record AccountSummary(
        AccountId account,
        AccountState state,
        Money closingBeforeCapitalisation,
        Money capitalisedInterest,
        Money finalBalance) {
}
