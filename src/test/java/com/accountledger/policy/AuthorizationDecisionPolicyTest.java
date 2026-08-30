package com.accountledger.policy;

import static com.accountledger.testkit.Assert.assertFalse;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.money.Money;
import com.accountledger.testkit.Test;
import java.util.Currency;

public class AuthorizationDecisionPolicyTest {

    private static final Currency AED = Currency.getInstance("AED");
    private final AuthorizationDecisionPolicy policy = new AvailableBalanceDecisionPolicy();

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    @Test("A hold is approved while available balance stays at or above zero")
    void approvedWhileAvailableStaysNonNegative() {
        assertTrue(policy.approves(aed("250.00"), aed("0.00"), aed("200.00")),
                "250 less a 200 hold leaves 50 available");
    }

    @Test("Exactly zero is approved: the rule says at or above zero")
    void exactlyZeroIsApproved() {
        assertTrue(policy.approves(aed("200.00"), aed("0.00"), aed("200.00")),
                "A rule meaning strictly positive would have had to say so");
    }

    @Test("Existing holds count against the decision even though they moved no money")
    void activeHoldsReduceAvailable() {
        assertFalse(policy.approves(aed("250.00"), aed("200.00"), aed("100.00")),
                "250 less 200 already held leaves 50, which will not cover 100");
    }

    @Test("Stream order is load-bearing: the same authorization flips on the balance it sees")
    void theSameAuthorizationGoesBothWays() {
        // Judged before a backdated debit arrives, and judged after it: nothing changes about
        // the authorization itself, only the balance that is in front of it.
        assertTrue(policy.approves(aed("465.00"), aed("0.00"), aed("90.00")),
                "Before: 465 available, a 90 hold is comfortable");
        assertFalse(policy.approves(aed("-155.00"), aed("0.00"), aed("90.00")),
                "After: the ledger stands at -155 and the hold would take it to -245");
    }

    @Test("A hold against an already negative balance is refused")
    void negativeBalanceRefuses() {
        assertFalse(policy.approves(aed("-0.01"), aed("0.00"), aed("0.01")),
                "There is nothing to reserve");
    }
}
