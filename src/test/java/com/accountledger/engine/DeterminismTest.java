package com.accountledger.engine;

import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.book.LedgerEntry;
import com.accountledger.fixture.CanonicalStream;
import com.accountledger.report.AccountSummary;
import com.accountledger.report.DayReport;
import com.accountledger.report.ReplayResult;
import com.accountledger.testkit.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Determinism, asserted rather than claimed.
 *
 * <p>Report types are records so that this comparison can be structural. If a day report were a
 * class with fields and a formatted {@code toString}, "the same report" would mean "the same
 * text", and two runs could agree on the text while disagreeing about a number the text does
 * not print.
 */
public class DeterminismTest {

    @Test("Two fresh engines on one stream produce identical results, day for day")
    void replayIsReproducible() {
        ReplayResult first = new LedgerEngine(CanonicalStream.configuration().build())
                .replay(CanonicalStream.events());
        ReplayResult second = new LedgerEngine(CanonicalStream.configuration().build())
                .replay(CanonicalStream.events());

        assertEquals(first.days().size(), second.days().size(), "Same number of days");
        for (int i = 0; i < first.days().size(); i++) {
            DayReport left = first.days().get(i);
            DayReport right = second.days().get(i);
            assertEquals(left, right, "Day " + (i + 1) + " should be identical");
        }
        assertEquals(first, second, "And so should the whole result");
    }

    @Test("One config used twice does not carry the first replay's interest remainder")
    void configIsReusableWithoutLeakingState() {
        // The interest policy carries a running remainder per account. Shared between replays it
        // would keep counting, and the second run would publish different figures for the same
        // stream. The config hands out a fresh policy instead, and this is what proves it.
        LedgerConfig shared = CanonicalStream.configuration().build();
        ReplayResult first = new LedgerEngine(shared).replay(CanonicalStream.events());
        ReplayResult second = new LedgerEngine(shared).replay(CanonicalStream.events());

        assertEquals(first, second, "The same config used twice gives the same answers twice");
        assertEquals(ScenarioReplay.summary(first, ACC_001).capitalisedInterest(),
                ScenarioReplay.summary(second, ACC_001).capitalisedInterest(),
                "And the second replay capitalises 0.82, not 1.64");
    }

    @Test("Entry sequence numbers are gapless from one, in a fixed order")
    void journalOrderIsFixed() {
        ReplayResult result = ScenarioReplay.replayScenario();
        List<Long> sequences = new ArrayList<>();
        for (LedgerEntry entry : result.journal()) {
            sequences.add(entry.sequence());
        }
        for (int i = 0; i < sequences.size(); i++) {
            assertEquals((long) (i + 1), (long) sequences.get(i),
                    "Sequence " + (i + 1) + " should be exactly that");
        }
        assertTrue(sequences.size() > 1, "There is a journal to check");
    }

    @Test("Every account reconciles: its entries sum to its final balance")
    void nothingCreatesMoney() {
        ReplayResult result = ScenarioReplay.replayScenario();

        for (AccountSummary summary : result.summaries()) {
            BigDecimal journalled = BigDecimal.ZERO;
            for (LedgerEntry entry : result.journal()) {
                if (entry.accountId().equals(summary.account())) {
                    journalled = journalled.add(entry.signedAmount());
                }
            }
            assertEquals(0, journalled.compareTo(summary.finalBalance().amount()),
                    summary.account() + " entries sum to " + journalled.toPlainString()
                            + " but the final balance is " + summary.finalBalance());
        }
    }
}
