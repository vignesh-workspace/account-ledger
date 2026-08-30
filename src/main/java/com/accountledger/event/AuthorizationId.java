package com.accountledger.event;

import java.util.Objects;

/**
 * Identifier of an authorization. Settlements reference one of these; a settlement whose
 * reference has never been seen is the unmatched case the engine must refuse.
 */
public record AuthorizationId(String value) {

    public AuthorizationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Authorization id must not be blank");
        }
    }

    public static AuthorizationId of(String value) {
        return new AuthorizationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
