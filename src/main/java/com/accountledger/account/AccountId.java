package com.accountledger.account;

import java.util.Objects;

/**
 * Opaque account identifier. A wrapper rather than a raw String so it cannot be swapped with
 * an event id or an authorization id at a call site.
 */
public record AccountId(String value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
