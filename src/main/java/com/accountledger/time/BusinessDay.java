package com.accountledger.time;

/**
 * An ordinal business day within the replay window. Day 1 is the first day.
 *
 * <p>Deliberately not a {@link java.time.LocalDate}. The specification names days only as
 * "Day 1" through "Day 6" with no calendar anchor, and mapping them onto real dates would
 * invent a timezone, a weekend rule and a holiday calendar that no requirement asks for.
 * The ordinal keeps the model honest about what it actually knows. See AMBIGUITIES.md.
 */
public record BusinessDay(int index) implements Comparable<BusinessDay> {

    public BusinessDay {
        if (index < 1) {
            throw new IllegalArgumentException("Business day index starts at 1, got " + index);
        }
    }

    public static BusinessDay of(int index) {
        return new BusinessDay(index);
    }

    public BusinessDay next() {
        return new BusinessDay(index + 1);
    }

    public boolean isBefore(BusinessDay other) {
        return index < other.index;
    }

    public boolean isAfter(BusinessDay other) {
        return index > other.index;
    }

    public boolean isOnOrBefore(BusinessDay other) {
        return index <= other.index;
    }

    @Override
    public int compareTo(BusinessDay other) {
        return Integer.compare(index, other.index);
    }

    @Override
    public String toString() {
        return "Day " + index;
    }
}
