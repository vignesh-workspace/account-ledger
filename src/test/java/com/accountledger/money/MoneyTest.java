package com.accountledger.money;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.testkit.Test;
import java.math.BigDecimal;
import java.util.Currency;

public class MoneyTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test("AED carries two decimals, BHD three, taken from the JDK not from a constant")
    public void scaleComesFromCurrency() {
        assertEquals("AED 1200.00", Money.of("1200.00", AED).toString(), "AED scale");
        assertEquals("BHD 10.000", Money.of("10.000", BHD).toString(), "BHD scale");
        assertEquals("JPY 500", Money.of("500", JPY).toString(), "JPY has no minor unit");
    }

    @Test("A short literal is padded to the currency scale rather than rejected")
    public void literalIsPaddedUpToScale() {
        assertEquals(Money.of("1200.00", AED), Money.of("1200", AED), "1200 == 1200.00 in AED");
        assertEquals(Money.of("10.000", BHD), Money.of("10.0", BHD), "10.0 == 10.000 in BHD");
    }

    @Test("A literal finer than the currency is refused, not silently rounded")
    public void overPreciseLiteralIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of("1.005", AED),
                "three decimals in AED must be refused");
        assertThrows(IllegalArgumentException.class,
                () -> Money.of("3.3335", BHD),
                "four decimals in BHD must be refused");
    }

    @Test("Computed values round HALF_UP through the explicit rounding entry point")
    public void computedValuesRoundHalfUp() {
        assertEquals(Money.of("1.01", AED),
                Money.round(new BigDecimal("1.005"), AED), "1.005 AED rounds up");
        assertEquals(Money.of("0.10", AED),
                Money.round(new BigDecimal("0.100"), AED), "exact value survives rounding");
        assertEquals(Money.of("3.334", BHD),
                Money.round(new BigDecimal("3.33350"), BHD), "BHD rounds at the third decimal");
    }

    @Test("Arithmetic stays at currency scale")
    public void arithmeticPreservesScale() {
        Money a = Money.of("1200.00", AED);
        Money b = Money.of("950.00", AED);
        assertEquals(Money.of("250.00", AED), a.minus(b), "1200 - 950");
        assertEquals(Money.of("2150.00", AED), a.plus(b), "1200 + 950");
        assertEquals(Money.of("-250.00", AED), b.minus(a), "950 - 1200 goes negative");
    }

    @Test("Mixing currencies throws rather than producing a number")
    public void crossCurrencyArithmeticThrows() {
        assertThrows(CurrencyMismatchException.class,
                () -> Money.of("1.00", AED).plus(Money.of("1.000", BHD)),
                "AED + BHD must not be representable");
        assertThrows(CurrencyMismatchException.class,
                () -> Money.of("1.00", AED).compareTo(Money.of("1.000", BHD)),
                "AED vs BHD must not be comparable");
    }

    @Test("Sign predicates treat zero as neither positive nor negative")
    public void zeroIsNeitherPositiveNorNegative() {
        Money zero = Money.zero(AED);
        assertTrue(zero.isZero(), "zero is zero");
        assertTrue(!zero.isNegative(), "zero is not negative: no overdraft fee at zero");
        assertTrue(!zero.isPositive(), "zero is not positive: no interest at zero");
        assertEquals("AED 0.00", zero.toString(), "zero carries the currency scale");
    }

    @Test("Interest multiplication defers rounding to the caller")
    public void multiplyReturnsUnroundedValue() {
        // 250.00 * 0.0004 = 0.100000 exactly; the point is that Money does not round it here.
        BigDecimal raw = Money.of("250.00", AED).multiplyUnrounded(new BigDecimal("0.0004"));
        assertEquals(0, raw.compareTo(new BigDecimal("0.1")), "unrounded product");
        assertTrue(raw.scale() > 2, "product keeps more precision than the currency scale");
    }

    @Test("Equality is value based and scale is normalised on construction")
    public void equalityIsValueBased() {
        assertEquals(Money.of("250.00", AED), Money.of("250.0", AED), "same value, same object");
        assertTrue(!Money.of("250.00", AED).equals(Money.of("250.000", BHD)),
                "same digits in different currencies are different money");
    }
}
