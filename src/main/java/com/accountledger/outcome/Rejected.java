package com.accountledger.outcome;

import com.accountledger.event.EventId;
import java.util.Objects;

/**
 * The instruction was refused and no money moved.
 *
 * <p>Carries the reason as a value so the report, the tests and any later analysis all agree
 * on what happened, rather than each parsing a sentence.
 */
public record Rejected(EventId eventId, RejectionReason reason, String detail) implements Outcome {

    public Rejected {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
    }

    public static Rejected of(EventId eventId, RejectionReason reason, Object... arguments) {
        return new Rejected(eventId, reason, reason.format(arguments));
    }

    @Override
    public String describe() {
        return "REFUSED " + eventId + "  " + reason + ": " + detail;
    }
}
