package com.accountledger.money;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.testkit.Test;
import java.util.Currency;
import java.util.List;

public class RemainderAllocatorTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test("Ten BHD in three parts is 3.333, 3.333, 3.334 and not 3.334 three times")
    public void tenBhdInThreeParts() {
        List<Money> parts = RemainderAllocator.split(Money.of("10.000", BHD), 3);
        assertEquals(List.of(Money.of("3.333", BHD), Money.of("3.333", BHD),
                Money.of("3.334", BHD)), parts, "instalment split");
        // 3.334 three times would be 10.002, which is not the amount anyone asked to credit.
        assertEquals(Money.of("10.000", BHD), sum(parts), "parts sum to the original total");
    }

    @Test("Parts always sum to the total across many shapes")
    public void partsAlwaysSumToTotal() {
        String[] amounts = {"10.00", "0.01", "100.00", "0.07", "999.99", "1.00"};
        for (String amount : amounts) {
            for (int parts = 1; parts <= 7; parts++) {
                Money total = Money.of(amount, AED);
                List<Money> split = RemainderAllocator.split(total, parts);
                assertEquals(total, sum(split),
                        "sum must be exact for " + amount + " into " + parts);
                assertEquals(parts, split.size(), "part count for " + amount);
            }
        }
    }

    @Test("Parts differ by at most one minor unit")
    public void partsDifferByAtMostOneMinorUnit() {
        List<Money> parts = RemainderAllocator.split(Money.of("1.00", AED), 3);
        assertEquals(List.of(Money.of("0.33", AED), Money.of("0.33", AED),
                Money.of("0.34", AED)), parts, "one AED in three");
    }

    @Test("The leftover lands on the last parts, fixed so results are reproducible")
    public void leftoverGoesToTheLastParts() {
        List<Money> parts = RemainderAllocator.split(Money.of("10.00", AED), 3);
        assertEquals(Money.of("3.33", AED), parts.get(0), "first part is the base amount");
        assertEquals(Money.of("3.34", AED), parts.get(2), "last part carries the leftover");
    }

    @Test("A negative total distributes its leftover the same way")
    public void negativeTotalsSplitCleanly() {
        Money total = Money.of("-10.00", AED);
        List<Money> parts = RemainderAllocator.split(total, 3);
        assertEquals(total, sum(parts), "negative parts still sum to the total");
        assertEquals(Money.of("-3.34", AED), parts.get(2), "last part carries the leftover");
    }

    @Test("A currency with no minor unit still splits exactly")
    public void zeroDecimalCurrencySplits() {
        List<Money> parts = RemainderAllocator.split(Money.of("100", JPY), 3);
        assertEquals(List.of(Money.of("33", JPY), Money.of("33", JPY), Money.of("34", JPY)),
                parts, "whole yen split");
    }

    @Test("Splitting into a single part returns the total unchanged")
    public void singlePartIsIdentity() {
        assertEquals(List.of(Money.of("7.77", AED)),
                RemainderAllocator.split(Money.of("7.77", AED), 1), "identity split");
    }

    @Test("A non-positive part count is refused")
    public void nonPositivePartCountRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> RemainderAllocator.split(Money.of("1.00", AED), 0), "zero parts");
        assertThrows(IllegalArgumentException.class,
                () -> RemainderAllocator.split(Money.of("1.00", AED), -2), "negative parts");
    }

    @Test("More parts than minor units leaves some parts at zero rather than failing")
    public void morePartsThanUnits() {
        List<Money> parts = RemainderAllocator.split(Money.of("0.02", AED), 5);
        assertEquals(Money.of("0.02", AED), sum(parts), "still sums exactly");
        assertTrue(parts.get(0).isZero(), "early parts are zero");
        assertEquals(Money.of("0.01", AED), parts.get(4), "last parts take the units");
    }

    private static Money sum(List<Money> parts) {
        Money total = Money.zero(parts.get(0).currency());
        for (Money part : parts) {
            total = total.plus(part);
        }
        return total;
    }
}
