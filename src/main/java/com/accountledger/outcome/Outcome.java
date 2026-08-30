package com.accountledger.outcome;

import com.accountledger.event.EventId;

/**
 * What the engine did with one instruction.
 *
 * <p>A refusal is a returned value, never a thrown exception. An exception would unwind the
 * replay and lose every event after the bad one, and it would make "the ledger declined this
 * authorization" — an ordinary, expected business answer — indistinguishable from a defect.
 * Every event in the stream produces exactly one outcome, and the day report prints all of
 * them, so a rejection is a record rather than a silence.
 *
 * <p>Sealed over exactly two cases. There is no third "partially accepted": an instruction
 * either moved money or did not.
 */
public sealed interface Outcome permits Accepted, Rejected {

    EventId eventId();

    /** Line printed in the day report. */
    String describe();

    default boolean isAccepted() {
        return this instanceof Accepted;
    }
}
