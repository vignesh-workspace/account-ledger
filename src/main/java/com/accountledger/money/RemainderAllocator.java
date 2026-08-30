package com.accountledger.money;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits an amount into parts that sum back to exactly the original.
 *
 * <p>The naive approach — divide, round each part, hope — does not sum. Ten in a
 * three-decimal currency divided three ways gives 3.333 three times, which is 9.999, or
 * 3.334 three times, which is 10.002. Neither is the amount that was asked for. The
 * difference has to go somewhere explicit.
 *
 * <p>This works in minor units, where the arithmetic is exact integer division, then hands
 * the leftover units out one each to the parts at the end. Nothing is discarded, so the
 * parts sum to the total by construction rather than by luck.
 *
 * <p>The leftover goes to the <em>last</em> parts rather than the first. Both are defensible
 * and neither is more correct; what matters is that the choice is fixed, because an
 * allocator that varies its answer makes a ledger irreproducible. Recorded in NUMBERS.md.
 */
public final class RemainderAllocator {

    private RemainderAllocator() {}

    /**
     * Divides {@code total} into {@code parts} amounts summing to exactly {@code total}.
     * Parts differ by at most one minor unit.
     */
    public static List<Money> split(Money total, int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("Cannot split into " + parts + " parts");
        }
        BigInteger minorUnits = total.amount().unscaledValue();
        BigInteger divisor = BigInteger.valueOf(parts);
        BigInteger base = minorUnits.divide(divisor);
        int leftover = minorUnits.subtract(base.multiply(divisor)).intValueExact();

        // A negative total leaves a negative remainder; hand out negative units the same way.
        int step = leftover < 0 ? -1 : 1;
        int toDistribute = Math.abs(leftover);

        int scale = total.currency().getDefaultFractionDigits();
        List<Money> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            boolean getsExtra = i >= parts - toDistribute;
            BigInteger units = getsExtra ? base.add(BigInteger.valueOf(step)) : base;
            result.add(new Money(new BigDecimal(units, scale), total.currency()));
        }
        return Collections.unmodifiableList(result);
    }
}
