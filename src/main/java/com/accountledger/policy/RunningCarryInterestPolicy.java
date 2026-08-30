package com.accountledger.policy;

import com.accountledger.account.Account;
import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily interest on positive closing balances, published so the daily figures sum exactly to
 * the capitalised total.
 *
 * <p>The rules require both that each day publishes a rounded accrual and that those accruals
 * sum exactly to what is capitalised. Those two requirements are in direct conflict, and the
 * conflict is not hypothetical: rounding each day independently over the scenario gives 0.83
 * while rounding the true total gives 0.82. One of them has to give way explicitly.
 *
 * <p>What is published each day is
 * <pre>round(cumulative unrounded accrual) - everything published so far</pre>
 * so the running total is always the correctly rounded total, and the remainder is carried
 * into the next day instead of being dropped or duplicated. Summing exactly is then a property
 * of the construction rather than something to assert and hope for.
 *
 * <p>The visible cost, and the report footnotes it: a day's published figure is not always
 * that day's balance times the rate, rounded. On the last day of the scenario the isolated
 * calculation gives 0.18 and this publishes 0.17. That difference is the carry doing its job.
 *
 * <p>The alternative — round each day, define the total as their sum — is trivially consistent
 * and drifts upward by a fraction of a minor unit per account per day, which is a real
 * profit-and-loss line at volume. It is implemented in test scope, where a long-horizon test
 * shows the two diverging.
 *
 * <p>This is the same largest-remainder discipline as
 * {@link com.accountledger.money.RemainderAllocator}, applied over time rather than over parts.
 */
public final class RunningCarryInterestPolicy implements InterestAccrualPolicy {

    private final BigDecimal dailyRate;
    private final Map<AccountId, BigDecimal> cumulativeUnrounded = new LinkedHashMap<>();
    private final Map<AccountId, Money> publishedSoFar = new LinkedHashMap<>();

    public RunningCarryInterestPolicy(BigDecimal dailyRate) {
        if (dailyRate.signum() < 0) {
            throw new IllegalArgumentException("A negative interest rate is not a fee: " + dailyRate);
        }
        this.dailyRate = dailyRate;
    }

    @Override
    public Money publish(Account account, Money closingBalance) {
        AccountId id = account.id();
        Money published = publishedSoFar.getOrDefault(id, Money.zero(account.currency()));

        // Only a positive balance earns. Zero is not positive, so it earns nothing -- and it is
        // not negative either, so it is charged nothing. Both halves of that come from one rule.
        BigDecimal accrual = closingBalance.isPositive()
                ? closingBalance.multiplyUnrounded(dailyRate)
                : BigDecimal.ZERO;

        BigDecimal cumulative = cumulativeUnrounded
                .getOrDefault(id, BigDecimal.ZERO)
                .add(accrual);
        cumulativeUnrounded.put(id, cumulative);

        Money target = Money.round(cumulative, account.currency());
        Money todays = target.minus(published);
        publishedSoFar.put(id, target);
        return todays;
    }

    @Override
    public Money publishedTotal(Account account) {
        return publishedSoFar.getOrDefault(account.id(), Money.zero(account.currency()));
    }
}
