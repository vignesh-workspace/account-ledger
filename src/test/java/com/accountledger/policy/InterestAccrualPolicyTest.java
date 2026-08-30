package com.accountledger.policy;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertFalse;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.account.Account;
import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

public class InterestAccrualPolicyTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final BigDecimal RATE = new BigDecimal("0.0004");

    private static final Account AED_ACCOUNT =
            new Account(AccountId.of("interest-bearing"), AED, BusinessDay.of(1));
    private static final Account BHD_ACCOUNT =
            new Account(AccountId.of("three-decimals"), BHD, BusinessDay.of(1));

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    private static List<Money> publishOver(
            InterestAccrualPolicy policy, Account account, List<Money> closingBalances) {
        List<Money> published = new ArrayList<>();
        for (Money balance : closingBalances) {
            published.add(policy.publish(account, balance));
        }
        return published;
    }

    private static List<Money> sixDayBalances() {
        return List.of(aed("250.00"), aed("250.00"), aed("650.00"),
                aed("465.00"), aed("-180.00"), aed("440.00"));
    }

    @Test("The daily figures sum to the capitalised total by construction, not by luck")
    void publishedAccrualsSumToTheCapitalisedTotal() {
        RunningCarryInterestPolicy policy = new RunningCarryInterestPolicy(RATE);
        List<Money> published = publishOver(policy, AED_ACCOUNT, sixDayBalances());

        assertEquals(List.of(aed("0.10"), aed("0.10"), aed("0.26"),
                        aed("0.19"), aed("0.00"), aed("0.17")),
                published, "The published sequence over the six days");

        Money sum = Money.zero(AED);
        for (Money day : published) {
            sum = sum.plus(day);
        }
        assertEquals(aed("0.82"), sum, "Which sums to the capitalised total");
        assertEquals(sum, policy.publishedTotal(AED_ACCOUNT), "And the policy agrees");
    }

    @Test("The last day publishes 0.17 where the isolated calculation says 0.18: the carry")
    void theCarryIsVisibleOnTheFinalDay() {
        RunningCarryInterestPolicy policy = new RunningCarryInterestPolicy(RATE);
        List<Money> published = publishOver(policy, AED_ACCOUNT, sixDayBalances());

        Money isolated = Money.round(aed("440.00").multiplyUnrounded(RATE), AED);
        assertEquals(aed("0.18"), isolated, "440.00 at the daily rate, rounded on its own");
        assertEquals(aed("0.17"), published.get(5),
                "But published as 0.17, because the earlier days already published 0.65");
    }

    @Test("Rounding each day independently gives 0.83 and disagrees with the true total")
    void theTwoReadingsActuallyDisagree() {
        AccrueThenSumInterestPolicy naive = new AccrueThenSumInterestPolicy(RATE);
        publishOver(naive, AED_ACCOUNT, sixDayBalances());

        assertEquals(aed("0.83"), naive.publishedTotal(AED_ACCOUNT),
                "Accrue-then-sum publishes 0.83");
        assertFalse(aed("0.82").equals(naive.publishedTotal(AED_ACCOUNT)),
                "The contradiction in the rules is real, not hypothetical");
    }

    @Test("Over a long horizon the carry stays exact while accrue-then-sum drifts upward")
    void longHorizonDrift() {
        // 12.75 at the daily rate accrues 0.0051 a day: just over half a minor unit, so the
        // naive reading rounds up every single day and never gives the excess back.
        Money balance = aed("12.75");
        int days = 1000;

        RunningCarryInterestPolicy carry = new RunningCarryInterestPolicy(RATE);
        AccrueThenSumInterestPolicy naive = new AccrueThenSumInterestPolicy(RATE);
        Money carrySum = Money.zero(AED);
        Money naiveSum = Money.zero(AED);
        for (int day = 0; day < days; day++) {
            carrySum = carrySum.plus(carry.publish(AED_ACCOUNT, balance));
            naiveSum = naiveSum.plus(naive.publish(AED_ACCOUNT, balance));
        }

        assertEquals(aed("5.10"), carrySum, "1000 days of 0.0051 is 5.10, and that is published");
        assertEquals(carrySum, carry.publishedTotal(AED_ACCOUNT),
                "Daily figures still sum exactly to the total after a thousand days");
        assertEquals(aed("10.00"), naiveSum, "The naive reading publishes 10.00");
        assertEquals(aed("4.90"), naiveSum.minus(carrySum),
                "A 4.90 overstatement on 5.10 actually owed, in one direction only");
    }

    @Test("Zero and negative balances earn nothing; zero is not positive")
    void onlyPositiveBalancesEarn() {
        RunningCarryInterestPolicy policy = new RunningCarryInterestPolicy(RATE);

        assertEquals(aed("0.00"), policy.publish(AED_ACCOUNT, aed("0.00")), "Zero earns nothing");
        assertEquals(aed("0.00"), policy.publish(AED_ACCOUNT, aed("-500.00")),
                "A negative balance earns nothing either; the fee is the separate rule");
        assertEquals(aed("0.00"), policy.publishedTotal(AED_ACCOUNT), "Nothing to capitalise");
    }

    @Test("Currency scale comes from the currency: three decimals accrue at three decimals")
    void scaleFollowsTheCurrency() {
        RunningCarryInterestPolicy policy = new RunningCarryInterestPolicy(RATE);
        Money first = policy.publish(BHD_ACCOUNT, Money.of("10.000", BHD));
        Money second = policy.publish(BHD_ACCOUNT, Money.of("10.000", BHD));

        assertEquals(Money.of("0.004", BHD), first, "10.000 at the daily rate is 0.004");
        assertEquals(Money.of("0.004", BHD), second, "And again the next day");
        assertEquals(Money.of("0.008", BHD), policy.publishedTotal(BHD_ACCOUNT),
                "Capitalising 0.008, at three decimals, with no constant anywhere");
        assertTrue(policy.publishedTotal(BHD_ACCOUNT).isPositive(), "And it is real money");
    }

    @Test("Accounts accrue independently, each carrying its own remainder")
    void carryIsPerAccount() {
        RunningCarryInterestPolicy policy = new RunningCarryInterestPolicy(RATE);
        policy.publish(AED_ACCOUNT, aed("650.00"));
        policy.publish(BHD_ACCOUNT, Money.of("10.000", BHD));

        assertEquals(aed("0.26"), policy.publishedTotal(AED_ACCOUNT), "One account");
        assertEquals(Money.of("0.004", BHD), policy.publishedTotal(BHD_ACCOUNT), "The other");
    }
}
