package com.accountledger.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An immutable amount in a single currency, always held at that currency's own scale.
 *
 * <p>Two construction paths, deliberately distinct:
 * <ul>
 *   <li>{@link #of} is for literals. It refuses input carrying more decimals than the
 *       currency allows, so a typo like {@code of("1.005", AED)} fails loudly instead of
 *       silently becoming 1.01.</li>
 *   <li>{@link #round} is for computed values. Rounding is what the caller asked for, so
 *       it is applied without complaint.</li>
 * </ul>
 * Cross-currency arithmetic throws {@link CurrencyMismatchException}. That is a programmer
 * error, not a business rejection, so it is never routed through the outcome log.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    /** Rounding for every currency-scale conversion in the system. See NUMBERS.md. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Working precision for intermediate arithmetic before rounding to currency scale. */
    public static final MathContext CALC = MathContext.DECIMAL128;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int scale = currency.getDefaultFractionDigits();
        if (amount.scale() != scale) {
            throw new IllegalArgumentException(
                    "Money must be held at the currency scale: " + currency.getCurrencyCode()
                            + " requires scale " + scale + " but got " + amount.scale()
                            + " (" + amount.toPlainString() + ")");
        }
    }

    /**
     * Builds from a literal. Throws if the literal is finer-grained than the currency,
     * rather than rounding it away.
     */
    public static Money of(String literal, Currency currency) {
        BigDecimal raw = new BigDecimal(literal);
        int scale = currency.getDefaultFractionDigits();
        BigDecimal exact;
        try {
            exact = raw.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Literal " + literal + " has more precision than "
                            + currency.getCurrencyCode() + " allows (scale " + scale
                            + "). Use Money.round(..) if rounding is intended.");
        }
        return new Money(exact, currency);
    }

    /** Rounds a computed value to the currency scale using {@link #ROUNDING}. */
    public static Money round(BigDecimal computed, Currency currency) {
        return new Money(computed.setScale(currency.getDefaultFractionDigits(), ROUNDING), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(
                BigDecimal.ZERO.setScale(currency.getDefaultFractionDigits()), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    /**
     * Multiplies at {@link #CALC} precision and returns the unrounded result. Interest needs
     * this: rounding must happen once, at the point the accrual is published, not here.
     */
    public BigDecimal multiplyUnrounded(BigDecimal factor) {
        return amount.multiply(factor, CALC);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }
}
