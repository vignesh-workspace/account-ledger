package com.accountledger.time;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.testkit.Test;

public class BusinessDayTest {

    @Test("Days order and compare by ordinal")
    public void daysOrderByIndex() {
        BusinessDay d2 = BusinessDay.of(2);
        BusinessDay d5 = BusinessDay.of(5);
        assertTrue(d2.isBefore(d5), "Day 2 before Day 5");
        assertTrue(d5.isAfter(d2), "Day 5 after Day 2");
        assertTrue(d2.isOnOrBefore(d2), "a day is on or before itself");
        assertTrue(d2.compareTo(d5) < 0, "comparable ordering");
    }

    @Test("Day zero is rejected: the window starts at Day 1")
    public void dayZeroIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BusinessDay.of(0), "Day 0 has no meaning in this window");
        assertThrows(IllegalArgumentException.class,
                () -> BusinessDay.of(-1), "negative days are not addressable");
    }

    @Test("Days render as they do in the specification")
    public void rendersAsDayN() {
        assertEquals("Day 4", BusinessDay.of(4).toString(), "printed form matches the spec");
    }

    @Test("next advances by one day")
    public void nextAdvances() {
        assertEquals(BusinessDay.of(3), BusinessDay.of(2).next(), "Day 2 -> Day 3");
    }
}
