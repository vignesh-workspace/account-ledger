package com.accountledger.engine;

import static com.accountledger.engine.ScenarioReplay.feeCount;
import static com.accountledger.engine.ScenarioReplay.replayScenario;
import static com.accountledger.engine.ScenarioReplay.row;
import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.fixture.CanonicalStream.ACC_002;
import static com.accountledger.fixture.CanonicalStream.aed;
import static com.accountledger.fixture.CanonicalStream.bhd;
import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerEntry;
import com.accountledger.fixture.CanonicalStream;
import com.accountledger.money.Money;
import com.accountledger.money.RemainderAllocator;
import com.accountledger.policy.RestatementFeePolicy;
import com.accountledger.report.ReplayResult;
import com.accountledger.testkit.Test;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * The criteria that are arithmetically wrong, refused by running them rather than by arguing
 * with them. Each of these corresponds to an entry in REJECTED.md, and the number in the
 * document is the number this test produces.
 */
public class RejectedCriteriaTest {

    private static final Currency AED = Currency.getInstance("AED");
    private final ReplayResult result = replayScenario();

    @Test("No reading of the fee rule produces one fee on day two")
    void oneFeeOnDayTwoIsUnreachable() {
        // Forward-only: one fee, and it lands on day five.
        assertEquals(1L, feeCount(result, ACC_001), "The shipped reading charges once");
        assertEquals(aed("25.00"), row(result, 5, ACC_001).feesCharged(), "On day five");
        assertEquals(aed("0.00"), row(result, 2, ACC_001).feesCharged(), "Never on day two");

        // Restatement: every day whose view re-derives negative is charged. Three of them do.
        ReplayResult restated = new LedgerEngine(CanonicalStream.configuration()
                .feePolicy(new RestatementFeePolicy(Map.of(AED, aed("25.00"))))
                .build())
                .replay(CanonicalStream.events());

        assertEquals(3L, feeCount(restated, ACC_001),
                "Restating history charges three fees, not one");
        assertEquals(List.of(2, 4, 5), feeValueDays(restated),
                "Value-dated on the days that re-derive negative once the debit is known");
    }

    @Test("After the reversal the balance is 440.00, not the 465.00 that stood before")
    void reversalDoesNotRestorePriorValues() {
        assertEquals(aed("465.00"), row(result, 4, ACC_001).closing(), "What stood before");
        assertEquals(aed("440.00"), row(result, 6, ACC_001).closing(), "What stands after");
        assertEquals(aed("25.00"), row(result, 5, ACC_001).feesCharged(),
                "The 25.00 difference is the fee, which was correctly charged and stays charged");
        assertEquals(aed("0.00"), row(result, 5, ACC_001).interestPublished(),
                "And day five's accrual is gone for good, not deferred");
    }

    @Test("Three instalments of 3.334 sum to 10.002, which is not the amount instructed")
    void theInstalmentSplitIsNotUniform() {
        Money claimed = bhd("3.334").plus(bhd("3.334")).plus(bhd("3.334"));
        assertEquals(bhd("10.002"), claimed, "Three of 3.334 is 10.002");

        List<Money> actual = RemainderAllocator.split(bhd("10.000"), 3);
        assertEquals(List.of(bhd("3.333"), bhd("3.333"), bhd("3.334")), actual,
                "The correct split at three decimals");
        assertEquals(bhd("10.000"), row(result, 5, ACC_002).closing(),
                "And the account receives exactly what was instructed");
    }

    @Test("Discarding the interest remainder contradicts the rule that the daily figures sum")
    void theRemainderCannotBeDiscarded() {
        Money capitalised = ScenarioReplay.summary(result, ACC_001).capitalisedInterest();
        Money daily = Money.zero(AED);
        for (int day = 1; day <= 6; day++) {
            daily = daily.plus(row(result, day, ACC_001).interestPublished());
        }
        assertEquals(capitalised, daily,
                "The published figures sum to the capitalised total exactly");
        assertEquals(aed("0.82"), capitalised, "0.82, with the remainder carried and not dropped");
    }

    private static List<Integer> feeValueDays(ReplayResult result) {
        List<Integer> days = new ArrayList<>();
        for (LedgerEntry entry : result.journal()) {
            if (entry.accountId().equals(ACC_001) && entry.type() == EntryType.FEE) {
                days.add(entry.valueDay().index());
            }
        }
        return days;
    }
}
