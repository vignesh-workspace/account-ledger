package com.accountledger.outcome;

import com.accountledger.event.EventId;
import java.util.Objects;

/** The instruction was carried out. {@code detail} says what it did, for the report. */
public record Accepted(EventId eventId, String detail) implements Outcome {

    public Accepted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(detail, "detail");
    }

    @Override
    public String describe() {
        return "OK      " + eventId + "  " + detail;
    }
}
