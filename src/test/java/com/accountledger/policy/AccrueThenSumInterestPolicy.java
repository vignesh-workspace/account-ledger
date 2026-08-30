package com.accountledger.policy;

import com.accountledger.account.Account;
import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rejected alternative: round each day in isolation and define the capitalised total as
 * the sum of those rounded figures.
 *
 * <p>It lives in test scope because it is not the design, and an implementation sitting in the
 * engine would eventually be selected by someone who found the configuration flag before the
 * argument. Here it has one job: make the drift measurable rather than merely asserted.
 *
 * <p>Trivially consistent, since the total is defined as the sum, and biased. Every day whose
 * true accrual sits above half a minor unit rounds up and the excess is never given back, so
 * the error accumulates in one direction. The long-horizon test runs both policies side by
 * side and shows this one publishing nearly twice what is owed.
 */
public final class AccrueThenSumInterestPolicy implements InterestAccrualPolicy {

    private final BigDecimal dailyRate;
    private final Map<AccountId, Money> publishedSoFar = new LinkedHashMap<>();

    public AccrueThenSumInterestPolicy(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    @Override
    public Money publish(Account account, Money closingBalance) {
        Money todays = closingBalance.isPositive()
                ? Money.round(closingBalance.multiplyUnrounded(dailyRate), account.currency())
                : Money.zero(account.currency());
        publishedSoFar.merge(account.id(), todays, Money::plus);
        return todays;
    }

    @Override
    public Money publishedTotal(Account account) {
        return publishedSoFar.getOrDefault(account.id(), Money.zero(account.currency()));
    }
}
