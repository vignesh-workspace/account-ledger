package com.accountledger.engine;

import static com.accountledger.engine.ScenarioReplay.balanceAsOf;
import static com.accountledger.engine.ScenarioReplay.feeCount;
import static com.accountledger.engine.ScenarioReplay.outcome;
import static com.accountledger.engine.ScenarioReplay.reasonFor;
import static com.accountledger.engine.ScenarioReplay.replayScenario;
import static com.accountledger.engine.ScenarioReplay.row;
import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.fixture.CanonicalStream.aed;
import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.fixture.CanonicalStream;
import com.accountledger.outcome.RejectionReason;
import com.accountledger.report.ReplayResult;
import com.accountledger.testkit.Test;

/** What the engine decided, and why, on the instructions the scenario disputes. */
public class ScenarioDecisionsTest {

    private final ReplayResult result = replayScenario();

    @Test("The day two balance evaluated on day five is -370.00")
    void dayTwoRestatedFromDayFive() {
        assertEquals(aed("250.00"), balanceAsOf(result, ACC_001, 2, 2),
                "As day two saw itself");
        assertEquals(aed("-370.00"), balanceAsOf(result, ACC_001, 2, 5),
                "1200 - 950 - 620, once the backdated debit is known");
    }

    @Test("A settlement quoting an authorization that was never approved moves no funds")
    void unmatchedSettlementIsRefused() {
        assertEquals(RejectionReason.UNKNOWN_AUTHORIZATION, reasonFor(result, "E6"),
                "Refused for the reason it was actually refused for");
        assertEquals(aed("465.00"), row(result, 4, ACC_001).closing(),
                "650 less only the 185 that did settle: the 180 never moved");
    }

    @Test("The second authorization is declined on the balance in front of it")
    void authorizationDeclined() {
        assertEquals(RejectionReason.INSUFFICIENT_AVAILABLE_BALANCE, reasonFor(result, "E8"),
                "Declined, and recorded in the day report rather than thrown");
        assertTrue(outcome(result, "E8").describe().contains("-245.00"),
                "The refusal shows what available would have become");
    }

    @Test("Order is authoritative: judged before the backdated debit, the same hold is approved")
    void streamOrderDecidesTheAnswer() {
        // Same events, same amounts, same days. Only the position of the authorization in the
        // stream moves, from after the backdated debit to before it.
        ReplayResult reordered = new LedgerEngine(CanonicalStream.configuration().build())
                .replay(CanonicalStream.eventsWithAuthorizationJudgedFirst());

        assertEquals(RejectionReason.INSUFFICIENT_AVAILABLE_BALANCE, reasonFor(result, "E8"),
                "As given, it is declined");
        assertTrue(ScenarioReplay.outcome(reordered, "E8").isAccepted(),
                "Moved one place earlier, the identical authorization is approved");
    }

    @Test("Exactly one fee is charged, and it lands on the day whose own balance is negative")
    void oneFeeOnDayFive() {
        assertEquals(1L, feeCount(result, ACC_001), "One fee in the whole window");
        assertEquals(aed("0.00"), row(result, 2, ACC_001).feesCharged(), "Not on day two");
        assertEquals(aed("0.00"), row(result, 4, ACC_001).feesCharged(), "Not on day four");
        assertEquals(aed("25.00"), row(result, 5, ACC_001).feesCharged(), "On day five");
    }

    @Test("A reversal reverses an entry, not history: the fee stands and the decline stands")
    void reversalDoesNotRestoreThePast() {
        assertTrue(outcome(result, "E9").isAccepted(), "The reversal itself is accepted");
        assertEquals(1L, feeCount(result, ACC_001), "The fee booked on day five is still there");
        assertEquals(aed("440.00"), row(result, 6, ACC_001).closing(),
                "440.00, not the 465.00 that stood before the debit");
        assertEquals(RejectionReason.INSUFFICIENT_AVAILABLE_BALANCE, reasonFor(result, "E8"),
                "And the authorization declined on day five is not retroactively approved");
    }

    @Test("A hold reduces available balance without moving the ledger balance")
    void holdsDoNotMoveTheLedgerBalance() {
        assertEquals(aed("250.00"), row(result, 1, ACC_001).closing(), "Before the hold");
        assertEquals(aed("250.00"), row(result, 2, ACC_001).closing(),
                "After a 200.00 hold, the ledger balance is the same number");
        assertEquals(aed("200.00"), row(result, 2, ACC_001).holds(), "The hold is real");
        assertEquals(aed("50.00"), row(result, 2, ACC_001).available(), "And available fell by it");
    }
}
