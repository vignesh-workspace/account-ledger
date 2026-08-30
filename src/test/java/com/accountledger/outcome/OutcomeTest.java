package com.accountledger.outcome;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertFalse;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.event.EventId;
import com.accountledger.testkit.Test;
import java.util.Locale;

public class OutcomeTest {

    private static final EventId EVENT = EventId.of("subject");

    @Test("A refusal is a value carrying a reason, not a sentence to be parsed")
    void rejectionCarriesItsReasonAsData() {
        Rejected rejected = Rejected.of(EVENT, RejectionReason.UNKNOWN_AUTHORIZATION, "some-auth");

        assertEquals(RejectionReason.UNKNOWN_AUTHORIZATION, rejected.reason(),
                "The reason is comparable without matching prose");
        assertTrue(rejected.detail().contains("some-auth"),
                "The detail names the thing that was refused");
        assertFalse(rejected.isAccepted(), "A refusal is not an acceptance");
    }

    @Test("Report wording does not vary with the default locale of the machine")
    void formattingIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String turkish = RejectionReason.ALREADY_REVERSED.format(7);
            Locale.setDefault(Locale.forLanguageTag("en-US"));
            String english = RejectionReason.ALREADY_REVERSED.format(7);

            assertEquals(english, turkish,
                    "A report built in another locale must compare equal to this one");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test("Every outcome prints itself for the day report")
    void bothCasesDescribeThemselves() {
        Outcome accepted = new Accepted(EVENT, "booked 10.00");
        Outcome rejected = Rejected.of(EVENT, RejectionReason.ALREADY_REVERSED, 3);

        assertTrue(accepted.describe().contains("subject"), "The accepted line names its event");
        assertTrue(rejected.describe().contains("ALREADY_REVERSED"),
                "The refused line names the reason, so a report can be grepped by it");
    }

    @Test("Acceptance is decided by the case, not by a boolean someone can set wrong")
    void acceptanceIsStructural() {
        assertTrue(new Accepted(EVENT, "did something").isAccepted(), "Accepted is accepted");
        assertFalse(Rejected.of(EVENT, RejectionReason.UNKNOWN_ENTRY, EVENT).isAccepted(),
                "Rejected is not");
    }
}
