package com.accountledger.policy;

import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One fee, on the day being closed, when that day's own closing balance is negative.
 *
 * <p>This is the forward-only reading, and it is the one the engine ships with. A day is
 * assessed once, against the balance as known on that day, and the fee is booked with value
 * date equal to the day assessed, exactly as the rule states. Days that have already closed
 * are not revisited, even when a backdated entry later makes their view negative: reopening a
 * closed day is a restatement run, which is a different piece of machinery with an operational
 * approval gate around it, and it is named in the cuts rather than half-built.
 *
 * <p>Zero draws no fee. Zero is not negative, and the rule says negative.
 *
 * <p>The fee is configured per currency and there is no conversion. A single amount applied to
 * every currency would silently assert that twenty-five dirhams and twenty-five dinars are the
 * same charge, which is roughly a factor of ten. A fee falling due in a currency with no
 * configured amount throws: it is a gap in the configuration, and inventing a number or
 * skipping the fee would both be worse than stopping.
 */
public final class FlatOverdraftFeePolicy implements OverdraftFeePolicy {

    private final Map<Currency, Money> feesByCurrency;

    public FlatOverdraftFeePolicy(Map<Currency, Money> feesByCurrency) {
        this.feesByCurrency = new LinkedHashMap<>(feesByCurrency);
    }

    @Override
    public List<FeeAssessment> assess(FeeContext context) {
        BusinessDay day = context.day();
        Money closing = context.book().balanceAsOf(context.account().id(), day, day);
        if (!closing.isNegative()) {
            return List.of();
        }
        Currency currency = context.account().currency();
        Money fee = feesByCurrency.get(currency);
        if (fee == null) {
            throw new IllegalStateException(
                    "An overdraft fee fell due on " + context.account().id() + " but no fee is "
                            + "configured for " + currency.getCurrencyCode()
                            + ". Configure one; there is no conversion rate to invent.");
        }
        return List.of(new FeeAssessment(fee, day,
                "closing balance " + closing + " is negative"));
    }
}
