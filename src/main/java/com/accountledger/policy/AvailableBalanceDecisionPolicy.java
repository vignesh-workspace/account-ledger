package com.accountledger.policy;

import com.accountledger.money.Money;

/**
 * Approves while available balance stays at or above zero once the new hold is applied:
 * <pre>ledger balance - active holds - proposed hold &gt;= 0</pre>
 *
 * <p>The ledger balance is the one known on the day the authorization is judged. That is what
 * makes stream order load-bearing rather than incidental: judged before a backdated debit
 * lands, an authorization sees a positive balance and is approved; judged after, it sees the
 * restated balance and is declined. Same authorization, same account, different answer, and
 * the difference is entirely the order the instructions arrived in.
 *
 * <p>Exactly zero is approved. The rule says at or above zero, and a rule that meant strictly
 * positive would have to say so.
 */
public final class AvailableBalanceDecisionPolicy implements AuthorizationDecisionPolicy {

    @Override
    public boolean approves(Money ledgerBalance, Money activeHolds, Money proposedHold) {
        Money availableAfter = ledgerBalance.minus(activeHolds).minus(proposedHold);
        return !availableAfter.isNegative();
    }
}
