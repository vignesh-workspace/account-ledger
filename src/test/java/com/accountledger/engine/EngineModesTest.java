package com.accountledger.engine;

import static com.accountledger.engine.ScenarioReplay.outcome;
import static com.accountledger.engine.ScenarioReplay.reasonFor;
import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.fixture.CanonicalStream.ACC_002;
import static com.accountledger.fixture.CanonicalStream.aed;
import static com.accountledger.fixture.CanonicalStream.bhd;
import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.account.AccountState;
import com.accountledger.event.Credit;
import com.accountledger.event.EventId;
import com.accountledger.event.EventStream;
import com.accountledger.event.LedgerEvent;
import com.accountledger.fixture.CanonicalStream;
import com.accountledger.outcome.Outcome;
import com.accountledger.outcome.Rejected;
import com.accountledger.outcome.RejectionReason;
import com.accountledger.report.ReplayResult;
import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.List;

/**
 * The behaviours that are configuration rather than rule, plus the ingest refusals. Each one
 * turns an assumption that would otherwise sit in a comment into something that fails.
 */
public class EngineModesTest {

    private static ReplayResult replay(LedgerConfig config, List<LedgerEvent> events) {
        return new LedgerEngine(config).replay(events);
    }

    @Test("Under strict arrival order, an instruction for a day already passed is refused")
    void strictModeRefusesALateArrival() {
        // The stream lists a reversal booking on day six before a credit booking on day five.
        // Read as a tape, day five has closed by the time that credit turns up.
        ReplayResult strict = replay(
                CanonicalStream.configuration().strictArrivalOrder(true).build(),
                CanonicalStream.events());

        assertEquals(RejectionReason.LATE_ARRIVAL_TO_CLOSED_DAY, reasonFor(strict, "E10-1"),
                "Refused as a late arrival rather than silently backdated");
        assertEquals(bhd("0.000"),
                ScenarioReplay.summary(strict, ACC_002).finalBalance(),
                "So none of the instalments land and the account finishes empty");
    }

    @Test("By default the same instruction is processed on the day it says it was booked")
    void bucketModeAcceptsTheSameInstruction() {
        ReplayResult bucketed = replay(
                CanonicalStream.configuration().build(), CanonicalStream.events());

        assertTrue(outcome(bucketed, "E10-1").isAccepted(),
                "The default reading groups by booking day and processes days ascending");
        assertEquals(bhd("10.008"),
                ScenarioReplay.summary(bucketed, ACC_002).finalBalance(), "And the money lands");
    }

    @Test("Closing an account releases live holds and refuses later entries")
    void closureIsJournalledAndEnforced() {
        List<LedgerEvent> events = EventStream.builder()
                .credit("c1", 1, ACC_001, aed("500.00"), 1)
                .authorization("a1", 2, ACC_001, "hold-one", aed("100.00"), 2)
                .closeAccount("x1", 3, ACC_001)
                .credit("c2", 4, ACC_001, aed("50.00"), 4)
                .closeAccount("x2", 5, ACC_001)
                .build();
        ReplayResult result = replay(CanonicalStream.configuration().build(), events);

        assertTrue(outcome(result, "x1").isAccepted(), "The closure is accepted");
        assertTrue(outcome(result, "x1").describe().contains("hold-one"),
                "And says which holds it released");
        assertEquals(RejectionReason.ACCOUNT_CLOSED, reasonFor(result, "c2"),
                "A later credit is refused");
        assertEquals(RejectionReason.ACCOUNT_ALREADY_CLOSED, reasonFor(result, "x2"),
                "And so is a second closure");
        assertEquals(AccountState.CLOSED,
                ScenarioReplay.summary(result, ACC_001).state(), "The account ends closed");
        assertEquals(aed("500.00"),
                ScenarioReplay.summary(result, ACC_001).closingBeforeCapitalisation(),
                "Closing keeps the money it had; nothing is deleted");
        // Interest accrued while the account was open is still owed, so it capitalises at the
        // end of the window even though the account has since closed. Recorded as an ambiguity.
        assertEquals(aed("500.40"), ScenarioReplay.summary(result, ACC_001).finalBalance(),
                "Two days of accrual are paid even though the account closed on day three");
    }

    @Test("An instruction dated outside the window belongs to no day and is listed separately")
    void outOfWindowIsRefusedAtIngest() {
        List<LedgerEvent> events = EventStream.builder()
                .credit("inside", 1, ACC_001, aed("10.00"), 1)
                .credit("outside", 9, ACC_001, aed("10.00"), 9)
                .build();
        ReplayResult result = replay(CanonicalStream.configuration().build(), events);

        assertEquals(1, result.ingestRejections().size(), "One instruction had nowhere to go");
        Outcome refused = result.ingestRejections().get(0);
        assertEquals(RejectionReason.DAY_OUTSIDE_WINDOW, ((Rejected) refused).reason(),
                "Refused for being outside the window");
        assertTrue(outcome(result, "inside").isAccepted(), "The rest of the stream is unaffected");
    }

    @Test("A repeated event id is refused for being a repeat, before anything else is judged")
    void duplicateEventIdIsRefused() {
        // Built by hand: the stream builder refuses duplicates outright, so this is the case
        // where a stream arrives from somewhere that did not check.
        LedgerEvent first = new Credit(EventId.of("same"), BusinessDay.of(1), ACC_001,
                aed("10.00"), BusinessDay.of(1));
        LedgerEvent second = new Credit(EventId.of("same"), BusinessDay.of(2), ACC_001,
                aed("999.00"), BusinessDay.of(2));
        ReplayResult result = replay(
                CanonicalStream.configuration().build(), List.of(first, second));

        List<Outcome> both = new ArrayList<>();
        for (var day : result.days()) {
            for (Outcome candidate : day.outcomes()) {
                if (candidate.eventId().value().equals("same")) {
                    both.add(candidate);
                }
            }
        }
        assertEquals(2, both.size(), "Both appearances are reported; neither is a silence");
        assertTrue(both.get(0).isAccepted(), "The first one is carried out");
        assertEquals(RejectionReason.DUPLICATE_EVENT_ID, ((Rejected) both.get(1)).reason(),
                "The second is refused as a duplicate");
        assertEquals(aed("10.00"),
                ScenarioReplay.summary(result, ACC_001).closingBeforeCapitalisation(),
                "And the 999.00 never moved");
    }
}
