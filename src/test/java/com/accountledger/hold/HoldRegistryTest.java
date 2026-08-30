package com.accountledger.hold;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertFalse;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.account.AccountId;
import com.accountledger.event.AuthorizationId;
import com.accountledger.money.Money;
import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;
import java.util.Currency;
import java.util.List;

public class HoldRegistryTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final AccountId ACCOUNT = AccountId.of("held-account");
    private static final AccountId OTHER = AccountId.of("other-account");

    private final HoldRegistry registry = new HoldRegistry();

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    private Hold place(String id, AccountId account, String amount, int day) {
        Hold hold = new Hold(AuthorizationId.of(id), account, aed(amount), BusinessDay.of(day));
        registry.place(hold);
        return hold;
    }

    @Test("An unknown authorization is empty, not an exception: schemes do send them")
    void unknownAuthorizationIsEmpty() {
        assertFalse(registry.find(AuthorizationId.of("never-approved")).isPresent(),
                "find returns empty so the engine can turn it into a rejection");
        assertFalse(registry.stateOf(AuthorizationId.of("never-approved")).isPresent(),
                "and so does the state lookup");
    }

    @Test("Only active holds reduce the available balance")
    void onlyActiveHoldsCount() {
        place("first", ACCOUNT, "200.00", 2);
        place("second", ACCOUNT, "50.00", 2);
        assertEquals(aed("250.00"), registry.activeTotalFor(ACCOUNT, AED), "Both are live");

        registry.close(AuthorizationId.of("first"), AuthState.SETTLED);
        assertEquals(aed("50.00"), registry.activeTotalFor(ACCOUNT, AED),
                "A settled authorization stops holding anything");
    }

    @Test("Holds on another account do not reduce this account's available balance")
    void holdsAreScopedToTheirAccount() {
        place("mine", ACCOUNT, "200.00", 2);
        place("theirs", OTHER, "999.00", 2);

        assertEquals(aed("200.00"), registry.activeTotalFor(ACCOUNT, AED), "Only my hold");
        assertEquals(aed("0.00"), registry.activeTotalFor(AccountId.of("empty"), AED),
                "An account with no holds answers zero in its own currency");
    }

    @Test("The terminal states stay distinct; settled and released are not the same outcome")
    void terminalStatesAreNotInterchangeable() {
        place("settled", ACCOUNT, "10.00", 1);
        place("released", ACCOUNT, "10.00", 1);
        registry.close(AuthorizationId.of("settled"), AuthState.SETTLED);
        registry.close(AuthorizationId.of("released"), AuthState.RELEASED);

        assertEquals(AuthState.SETTLED, registry.stateOf(AuthorizationId.of("settled")).orElseThrow(),
                "A settlement consumed this one");
        assertEquals(AuthState.RELEASED, registry.stateOf(AuthorizationId.of("released")).orElseThrow(),
                "This one gave the funds back without settling");
        assertFalse(registry.isActive(AuthorizationId.of("settled")), "Neither is still holding");
    }

    @Test("Closing to ACTIVE is refused: it is the state being left, not one to arrive at")
    void activeIsNotATerminalState() {
        place("live", ACCOUNT, "10.00", 1);
        assertThrows(IllegalArgumentException.class,
                () -> registry.close(AuthorizationId.of("live"), AuthState.ACTIVE),
                "ACTIVE should be refused as a terminal state");
    }

    @Test("Authorizations list in approval order, so the report does not reshuffle")
    void listingOrderIsApprovalOrder() {
        place("zebra", ACCOUNT, "10.00", 1);
        place("alpha", ACCOUNT, "10.00", 2);

        assertEquals(List.of(AuthorizationId.of("zebra"), AuthorizationId.of("alpha")),
                registry.idsFor(ACCOUNT), "Approval order, not alphabetical and not hash order");
    }

    @Test("Placing the same authorization twice is a defect once ingest has run")
    void duplicatePlacementThrows() {
        place("once", ACCOUNT, "10.00", 1);
        assertThrows(IllegalStateException.class, () -> place("once", ACCOUNT, "10.00", 2),
                "A duplicate approval should throw");
    }

    @Test("A hold reserves a positive amount; a zero hold reserves nothing")
    void holdsArePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new Hold(AuthorizationId.of("empty"), ACCOUNT, aed("0.00"), BusinessDay.of(1)),
                "A zero hold should be refused");
        assertTrue(place("real", ACCOUNT, "0.01", 1).amount().isPositive(), "A real hold is positive");
    }
}
