package com.accountledger.policy;

import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerEntry;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The other reading of the fee rule, built so the argument about it can be settled by running
 * it instead of by asserting it in prose.
 *
 * <p>Where the shipped policy assesses only the day being closed, this one re-derives every
 * earlier day as of today's knowledge and charges a fee for each day that now reads negative
 * and has not already been charged. It is what "the fee follows the money to the day the money
 * belongs to" actually means once it is written down.
 *
 * <p>Run against the scenario it produces <strong>three</strong> fees, value-dated on the days
 * whose views re-derive negative once the backdated debit is known. That is the point: the
 * criterion claiming exactly one fee, assessed on day two, is not reachable under either
 * reading. Forward-only gives one fee on day five. Restatement gives three. Nothing gives one
 * fee on day two.
 *
 * <p>It lives in test scope because it is a demonstration, not an option. Shipping it would
 * offer a restatement engine without the back-valuation controls and the operational approval
 * gate that make one safe to run.
 */
public final class RestatementFeePolicy implements OverdraftFeePolicy {

    private final Map<Currency, Money> feesByCurrency;

    public RestatementFeePolicy(Map<Currency, Money> feesByCurrency) {
        this.feesByCurrency = Map.copyOf(feesByCurrency);
    }

    @Override
    public List<FeeAssessment> assess(FeeContext context) {
        Money fee = feesByCurrency.get(context.account().currency());
        if (fee == null) {
            throw new IllegalStateException(
                    "No fee configured for " + context.account().currency().getCurrencyCode());
        }
        Set<BusinessDay> alreadyCharged = new LinkedHashSet<>();
        for (LedgerEntry entry : context.book().entriesFor(context.account().id())) {
            if (entry.type() == EntryType.FEE) {
                alreadyCharged.add(entry.valueDay());
            }
        }

        List<FeeAssessment> due = new ArrayList<>();
        for (BusinessDay day = context.windowStart();
                day.isOnOrBefore(context.day());
                day = day.next()) {
            if (alreadyCharged.contains(day)) {
                continue;
            }
            // Every earlier day re-evaluated with everything known today, which is exactly the
            // move the shipped policy refuses to make.
            Money restated = context.book()
                    .balanceAsOf(context.account().id(), day, context.day());
            if (restated.isNegative()) {
                due.add(new FeeAssessment(fee, day,
                        "re-derived on " + context.day() + ", " + day + " reads " + restated));
            }
        }
        return due;
    }
}
