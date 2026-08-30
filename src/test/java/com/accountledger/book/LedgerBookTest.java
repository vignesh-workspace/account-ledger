package com.accountledger.book;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertFalse;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.account.AccountId;
import com.accountledger.account.AccountRegistry;
import com.accountledger.event.EventId;
import com.accountledger.money.CurrencyMismatchException;
import com.accountledger.money.Money;
import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;
import java.util.Currency;

public class LedgerBookTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final AccountId ACCOUNT = AccountId.of("account-under-test");

    private final AccountRegistry registry = new AccountRegistry();
    private final LedgerBook book;

    public LedgerBookTest() {
        registry.open(ACCOUNT, AED, BusinessDay.of(1));
        book = new LedgerBook(registry);
    }

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    private LedgerEntry post(String event, EntryType type, String amount, int valueDay, int bookingDay) {
        return book.append(EventId.of(event), ACCOUNT, type, aed(amount),
                BusinessDay.of(valueDay), BusinessDay.of(bookingDay), null);
    }

    @Test("A balance needs both days: the same value day reads differently as knowledge grows")
    void balanceIsBitemporal() {
        post("in", EntryType.CREDIT, "1200.00", 1, 1);
        post("out", EntryType.DEBIT, "950.00", 1, 1);
        // Books three days late but takes value from day two, restating a reported day.
        post("backdated", EntryType.DEBIT, "620.00", 2, 5);

        assertEquals(aed("250.00"), book.balanceAsOf(ACCOUNT, BusinessDay.of(2), BusinessDay.of(2)),
                "On day two, the backdated debit had not arrived");
        assertEquals(aed("-370.00"), book.balanceAsOf(ACCOUNT, BusinessDay.of(2), BusinessDay.of(5)),
                "Asked again on day five, day two is 1200 - 950 - 620");
    }

    @Test("An entry contributes from its value day, not from the day it was booked")
    void valueDayDecidesWhichBalancesMove() {
        post("backdated", EntryType.DEBIT, "620.00", 2, 5);

        assertEquals(aed("0.00"), book.balanceAsOf(ACCOUNT, BusinessDay.of(1), BusinessDay.of(5)),
                "Day one is before the value day and is untouched");
        assertEquals(aed("-620.00"), book.balanceAsOf(ACCOUNT, BusinessDay.of(2), BusinessDay.of(5)),
                "Day two is on the value day and moves");
    }

    @Test("Sequence numbers are global and gapless, because entries arrive out of day order")
    void sequencesAreGaplessFromOne() {
        assertEquals(1L, post("a", EntryType.CREDIT, "10.00", 1, 1).sequence(), "First is 1");
        assertEquals(2L, post("b", EntryType.CREDIT, "10.00", 1, 6).sequence(), "Then 2");
        assertEquals(3L, post("c", EntryType.CREDIT, "10.00", 1, 3).sequence(),
                "Booking day going backwards does not renumber anything");
    }

    @Test("A correction is a new entry pointing back; the original is never touched")
    void reversalAppendsRatherThanMutates() {
        LedgerEntry original = post("backdated", EntryType.DEBIT, "620.00", 2, 5);
        LedgerEntry correction = book.append(EventId.of("undo"), ACCOUNT, EntryType.CREDIT,
                aed("620.00"), BusinessDay.of(2), BusinessDay.of(6), original.sequence());

        assertEquals(2, book.size(), "Both entries are in the book");
        assertEquals(original, book.entries().get(0), "The original is unchanged");
        assertTrue(correction.isCorrection(), "The correction knows what it corrects");
        assertTrue(book.isReversed(original.sequence()), "And the book can answer the question");
        assertEquals(aed("0.00"), book.balanceAsOf(ACCOUNT, BusinessDay.of(2), BusinessDay.of(6)),
                "Day two nets to zero once the correction is known");
        assertEquals(aed("-620.00"), book.balanceAsOf(ACCOUNT, BusinessDay.of(2), BusinessDay.of(5)),
                "But day five still saw the error, and always will");
    }

    @Test("Reversing an entry twice is a defect once ingest has run")
    void doubleReversalThrows() {
        LedgerEntry original = post("original", EntryType.DEBIT, "10.00", 1, 1);
        book.append(EventId.of("undo"), ACCOUNT, EntryType.CREDIT, aed("10.00"),
                BusinessDay.of(1), BusinessDay.of(2), original.sequence());

        assertThrows(IllegalStateException.class,
                () -> book.append(EventId.of("undo-again"), ACCOUNT, EntryType.CREDIT,
                        aed("10.00"), BusinessDay.of(1), BusinessDay.of(3), original.sequence()),
                "A second reversal of the same entry should throw");
    }

    @Test("Posting another currency to an account throws rather than booking wrong units")
    void currencyMismatchThrows() {
        assertThrows(CurrencyMismatchException.class,
                () -> book.append(EventId.of("wrong"), ACCOUNT, EntryType.CREDIT,
                        Money.of("10.000", BHD), BusinessDay.of(1), BusinessDay.of(1), null),
                "A BHD amount on an AED account is a wiring defect");
    }

    @Test("The entry list cannot be modified through the accessor")
    void entriesAreUnmodifiable() {
        post("a", EntryType.CREDIT, "10.00", 1, 1);
        assertThrows(UnsupportedOperationException.class, () -> book.entries().clear(),
                "The journal is append-only from the outside too");
    }

    @Test("Direction comes from the entry type, never from a stored negative")
    void directionIsCarriedByType() {
        assertEquals(1, EntryType.CREDIT.signum(), "A credit is money in");
        assertEquals(-1, EntryType.FEE.signum(), "A fee is money out");
        assertThrows(IllegalArgumentException.class,
                () -> post("negative", EntryType.CREDIT, "-10.00", 1, 1),
                "A negative amount should be refused at construction");
    }

    @Test("Only an opening entry may be zero; every other zero entry is noise in the report")
    void zeroIsOnlyValidForAnOpeningBalance() {
        LedgerEntry opening = post("opened", EntryType.OPENING, "0.00", 1, 1);
        assertEquals(aed("0.00"), opening.amount(), "An account may open with nothing in it");
        assertThrows(IllegalArgumentException.class,
                () -> post("empty", EntryType.CREDIT, "0.00", 1, 1),
                "A zero credit moves nothing and should be refused");
    }

    @Test("An event that booked no entry is findable as absent, not as zero")
    void sourceEventLookupIsOptional() {
        post("real", EntryType.CREDIT, "10.00", 1, 1);
        assertTrue(book.findBySourceEvent(EventId.of("real")).isPresent(), "Found");
        assertFalse(book.findBySourceEvent(EventId.of("rejected")).isPresent(),
                "A rejected event books nothing and must not look like a zero entry");
    }
}
