package com.accountledger.money;

import java.util.Currency;

/**
 * Thrown when arithmetic mixes currencies. Unchecked and never caught by the engine: a
 * mismatch means the caller wired the wrong account to the wrong event, which is a defect
 * to fix, not a business outcome to record.
 */
public class CurrencyMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    public CurrencyMismatchException(Currency left, Currency right) {
        super("Cannot combine " + left.getCurrencyCode() + " with " + right.getCurrencyCode());
    }
}
