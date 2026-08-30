package com.accountledger.event;

import java.util.Objects;

/** Identifier of an event in the journal, unique across the whole stream. */
public record EventId(String value) {

    public EventId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Event id must not be blank");
        }
    }

    public static EventId of(String value) {
        return new EventId(value);
    }

    /** Derives a child id for an event that expands into parts, such as instalments. */
    public EventId part(int index) {
        return new EventId(value + "-" + index);
    }

    @Override
    public String toString() {
        return value;
    }
}
