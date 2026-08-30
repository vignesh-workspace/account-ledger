package com.accountledger.engine;

import static com.accountledger.engine.ScenarioReplay.replayScenario;
import static com.accountledger.engine.ScenarioReplay.row;
import static com.accountledger.engine.ScenarioReplay.summary;
import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.fixture.CanonicalStream.ACC_002;
import static com.accountledger.fixture.CanonicalStream.aed;
import static com.accountledger.fixture.CanonicalStream.bhd;
import static com.accountledger.testkit.Assert.assertEquals;

import com.accountledger.report.ReplayResult;
import com.accountledger.testkit.Test;

/**
 * The scenario, day by day. These are the numbers the whole thing exists to produce, so they
 * are asserted as a table rather than one at a time in scattered tests.
 */
public class ScenarioReplayTest {

    private final ReplayResult result = replayScenario();

    @Test("Day 1: opening trading leaves 250.00 and accrues 0.10")
    void dayOne() {
        assertEquals(aed("250.00"), row(result, 1, ACC_001).closing(), "1200 in, 950 out");
        assertEquals(aed("0.00"), row(result, 1, ACC_001).holds(), "Nothing held yet");
        assertEquals(aed("0.00"), row(result, 1, ACC_001).feesCharged(), "Nothing to charge");
        assertEquals(aed("0.10"), row(result, 1, ACC_001).interestPublished(), "250.00 earns 0.10");
    }

    @Test("Day 2: the hold reduces available to 50.00 without moving the ledger balance")
    void dayTwo() {
        assertEquals(aed("250.00"), row(result, 2, ACC_001).closing(),
                "A hold books no entry, so the ledger balance does not move");
        assertEquals(aed("200.00"), row(result, 2, ACC_001).holds(), "But 200.00 is reserved");
        assertEquals(aed("50.00"), row(result, 2, ACC_001).available(), "Leaving 50.00 available");
        assertEquals(aed("0.10"), row(result, 2, ACC_001).interestPublished(),
                "Interest is on the ledger balance, not the available one");
    }

    @Test("Day 3: a credit takes the balance to 650.00")
    void dayThree() {
        assertEquals(aed("650.00"), row(result, 3, ACC_001).closing(), "650.00");
        assertEquals(aed("450.00"), row(result, 3, ACC_001).available(), "Still 200.00 held");
        assertEquals(aed("0.26"), row(result, 3, ACC_001).interestPublished(), "650.00 earns 0.26");
    }

    @Test("Day 4: the settlement books 185.00 and releases the rest of the hold")
    void dayFour() {
        assertEquals(aed("465.00"), row(result, 4, ACC_001).closing(), "650 less the 185 settled");
        assertEquals(aed("0.00"), row(result, 4, ACC_001).holds(),
                "A partial settlement closes the authorization and gives back the difference");
        assertEquals(aed("465.00"), row(result, 4, ACC_001).available(), "So all of it is available");
        assertEquals(aed("0.19"), row(result, 4, ACC_001).interestPublished(), "0.19, after carry");
    }

    @Test("Day 5: the backdated debit overdraws the day and draws the fee")
    void dayFive() {
        assertEquals(aed("-155.00"), row(result, 5, ACC_001).closingBeforeFees(),
                "465 less the backdated 620, which values on day two but lands here");
        assertEquals(aed("25.00"), row(result, 5, ACC_001).feesCharged(), "One fee");
        assertEquals(aed("-180.00"), row(result, 5, ACC_001).closing(), "Closing after the fee");
        assertEquals(aed("0.00"), row(result, 5, ACC_001).interestPublished(),
                "A negative balance earns nothing");
    }

    @Test("Day 6: the reversal restores 440.00, not 465.00")
    void daySix() {
        assertEquals(aed("440.00"), row(result, 6, ACC_001).closing(),
                "The 620 comes back; the 25.00 fee stays where it was booked");
        assertEquals(aed("0.17"), row(result, 6, ACC_001).interestPublished(),
                "0.17 published where the isolated sum says 0.18: the carry");
    }

    @Test("The window ends at 440.82 after capitalising 0.82")
    void finalPosition() {
        assertEquals(aed("440.00"), summary(result, ACC_001).closingBeforeCapitalisation(),
                "Day six closes on its own trading");
        assertEquals(aed("0.82"), summary(result, ACC_001).capitalisedInterest(),
                "Six days of accruals, summing exactly");
        assertEquals(aed("440.82"), summary(result, ACC_001).finalBalance(),
                "Capitalisation is applied after the final day's closing balance");
    }

    @Test("The three-decimal account splits 10.000 and finishes at 10.008")
    void theOtherAccount() {
        assertEquals(bhd("0.000"), row(result, 4, ACC_002).closing(), "Nothing until day five");
        assertEquals(bhd("10.000"), row(result, 5, ACC_002).closing(),
                "3.333 + 3.333 + 3.334, which is the instructed total and not 10.002");
        assertEquals(bhd("0.004"), row(result, 5, ACC_002).interestPublished(), "0.004 on day five");
        assertEquals(bhd("0.004"), row(result, 6, ACC_002).interestPublished(), "And again on six");
        assertEquals(bhd("0.008"), summary(result, ACC_002).capitalisedInterest(), "Capitalising 0.008");
        assertEquals(bhd("10.008"), summary(result, ACC_002).finalBalance(), "Finishing at 10.008");
    }
}
