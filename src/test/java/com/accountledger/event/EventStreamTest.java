package com.accountledger.event;

import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.fixture.CanonicalStream.ACC_002;
import static com.accountledger.fixture.CanonicalStream.aed;
import static com.accountledger.fixture.CanonicalStream.bhd;
import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.fixture.CanonicalStream;
import com.accountledger.testkit.Test;
import java.util.List;

public class EventStreamTest {

    @Test("The stream preserves submission order and never sorts by day")
    public void submissionOrderIsPreserved() {
        List<LedgerEvent> events = CanonicalStream.events();
        // The reversal books on day six but is written before the instalment credit, which
        // books on day five. If the builder sorted, this would come out the other way round.
        int reversalIndex = indexOf(events, "E9");
        int firstInstalmentIndex = indexOf(events, "E10-1");
        assertTrue(reversalIndex < firstInstalmentIndex,
                "later booking day still appears earlier in the stream");
        assertEquals(6, events.get(reversalIndex).bookingDay().index(), "reversal books day 6");
        assertEquals(5, events.get(firstInstalmentIndex).bookingDay().index(),
                "instalment books day 5");
    }

    @Test("A value day earlier than its booking day is accepted, not corrected")
    public void backdatedEventIsAccepted() {
        LedgerEvent backdated = find(CanonicalStream.events(), "E7");
        assertEquals(5, backdated.bookingDay().index(), "books on day 5");
        assertEquals(2, backdated.valueDay().index(), "takes value from day 2");
        assertTrue(backdated.valueDay().isBefore(backdated.bookingDay()),
                "the gap between the two days is the point");
    }

    @Test("Instalments expand into separate credits with derived ids")
    public void instalmentsExpandIntoDerivedEvents() {
        List<LedgerEvent> events = CanonicalStream.events();
        List<LedgerEvent> instalments = events.stream()
                .filter(e -> e.eventId().value().startsWith("E10-")).toList();
        assertEquals(3, instalments.size(), "three instalments");
        assertEquals(bhd("3.333"), instalments.get(0).amount(), "first instalment");
        assertEquals(bhd("3.334"), instalments.get(2).amount(), "last instalment carries remainder");
        for (LedgerEvent e : instalments) {
            assertEquals(ACC_002, e.accountId(), "instalments credit the second account");
        }
    }

    @Test("A duplicate event id is refused when the stream is built")
    public void duplicateEventIdRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> EventStream.builder()
                        .credit("E1", 1, ACC_001, aed("1.00"), 1)
                        .credit("E1", 2, ACC_001, aed("2.00"), 2)
                        .build(),
                "the same id twice must not build");
    }

    @Test("Direction is carried by the event kind, so amounts are always positive")
    public void amountsAreAlwaysPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> EventStream.builder().debit("X", 1, ACC_001, aed("-5.00"), 1),
                "a negative debit is a contradiction");
        assertThrows(IllegalArgumentException.class,
                () -> EventStream.builder().credit("X", 1, ACC_001, aed("-5.00"), 1),
                "a negative credit is a contradiction");
        for (LedgerEvent e : CanonicalStream.events()) {
            if (e.amount() != null) {
                assertTrue(e.amount().isPositive(), e.eventId() + " carries a positive amount");
            }
        }
    }

    @Test("A reversal carries no amount of its own")
    public void reversalHasNoAmount() {
        LedgerEvent reversal = find(CanonicalStream.events(), "E9");
        assertTrue(reversal.amount() == null,
                "the amount belongs to the entry being reversed, which is not resolved yet");
        assertEquals(2, reversal.valueDay().index(),
                "the reversal restates the same day the original affected");
    }

    @Test("An event cannot reverse itself")
    public void selfReversalRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> EventStream.builder().reversal("E9", 6, ACC_001, "E9", 2),
                "self reference must not build");
    }

    @Test("The stream is unmodifiable once built")
    public void streamIsUnmodifiable() {
        List<LedgerEvent> events = CanonicalStream.events();
        assertThrows(UnsupportedOperationException.class,
                () -> events.add(null), "a built stream cannot be appended to");
    }

    @Test("Every event describes itself for the day report")
    public void eventsDescribeThemselves() {
        assertEquals("CREDIT AED 1200.00", find(CanonicalStream.events(), "E1").describe(),
                "credit description");
        assertEquals("SETTLEMENT Auth-Z settles for AED 180.00",
                find(CanonicalStream.events(), "E6").describe(), "settlement description");
        assertEquals("REVERSAL of E7", find(CanonicalStream.events(), "E9").describe(),
                "reversal description");
    }

    private static LedgerEvent find(List<LedgerEvent> events, String id) {
        return events.stream().filter(e -> e.eventId().value().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no event " + id));
    }

    private static int indexOf(List<LedgerEvent> events, String id) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).eventId().value().equals(id)) {
                return i;
            }
        }
        throw new AssertionError("no event " + id);
    }
}
