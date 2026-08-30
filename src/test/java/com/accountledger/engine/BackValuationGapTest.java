package com.accountledger.engine;

import static com.accountledger.engine.ScenarioReplay.replayScenario;
import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.testkit.Assert.assertEquals;

import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerEntry;
import com.accountledger.report.ReplayResult;
import com.accountledger.testkit.Disabled;
import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;

/**
 * The known gap, kept as a test that fails rather than as a paragraph that does not.
 *
 * <p>A test in a file is checked every run. A limitation in a document is checked when somebody
 * happens to read it, and stops being true without anybody noticing. The runner also fails the
 * build if this test starts passing, so if the gap ever closes that is reported as news instead
 * of disappearing quietly.
 */
public class BackValuationGapTest {

    @Test("A backdated debit charges the fee on the day it overdrew, not the day it arrived")
    @Disabled("Forward-only assessment books the fee on the day the instruction arrived. "
            + "Charging it on the historical day the balance actually went negative needs a "
            + "back-valuation and restatement run, which is deliberately not built.")
    void feeFollowsTheValueDayOfTheEntryThatCausedIt() {
        ReplayResult result = replayScenario();

        // The debit books on day five and takes value from day two. The money left the account
        // on day two as far as the ledger is concerned, so day two is the day that was
        // overdrawn -- day five merely learned about it. A customer reading a statement sees
        // the balance go negative on day two and a fee dated three days later.
        //
        // What the engine does instead: it assesses the day it is closing, against the balance
        // as known on that day, and books the fee with value date equal to the day assessed.
        // Day two closed clean and is never reopened.
        //
        // What closing this gap would take is not a different line of code. It needs a
        // back-valuation run that reopens closed days, re-derives their balances against
        // today's knowledge, and works out which assessments would have been made and which
        // already were -- and, because that run moves money on days a customer has already been
        // told about, an operational approval gate in front of it. Both are named in the
        // architecture document as cut, with the risk that defers.
        //
        // RestatementFeePolicy in test scope shows what the arithmetic does once history is
        // reopened: three fees rather than one. That is the other half of why this is a gap and
        // not an oversight. The restating answer is not obviously the right one either.
        assertEquals(BusinessDay.of(2), feeValueDay(result),
                "The fee should be dated the day the balance actually went negative");
    }

    private static BusinessDay feeValueDay(ReplayResult result) {
        for (LedgerEntry entry : result.journal()) {
            if (entry.accountId().equals(ACC_001) && entry.type() == EntryType.FEE) {
                return entry.valueDay();
            }
        }
        throw new AssertionError("No fee was charged at all");
    }
}
