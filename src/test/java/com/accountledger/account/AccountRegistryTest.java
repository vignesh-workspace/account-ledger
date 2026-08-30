package com.accountledger.account;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertFalse;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;
import java.util.Currency;
import java.util.List;

public class AccountRegistryTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final AccountId FIRST = AccountId.of("first");
    private static final AccountId SECOND = AccountId.of("second");

    @Test("Registration order is preserved, so day reports do not reshuffle between runs")
    void iterationOrderIsRegistrationOrder() {
        AccountRegistry registry = new AccountRegistry();
        registry.open(SECOND, AED, BusinessDay.of(1));
        registry.open(FIRST, AED, BusinessDay.of(1));

        assertEquals(List.of(SECOND, FIRST),
                registry.all().stream().map(Account::id).toList(),
                "Accounts should come back in the order they were opened");
    }

    @Test("Opening the same account twice throws: no stream can cause it, so it is a defect")
    void duplicateOpenThrows() {
        AccountRegistry registry = new AccountRegistry();
        registry.open(FIRST, AED, BusinessDay.of(1));

        assertThrows(IllegalStateException.class,
                () -> registry.open(FIRST, AED, BusinessDay.of(2)),
                "A second open of the same id should throw");
    }

    @Test("An unknown account reaching require() is a defect, not a rejection")
    void requireThrowsForUnknownAccount() {
        AccountRegistry registry = new AccountRegistry();

        assertThrows(IllegalStateException.class, () -> registry.require(FIRST),
                "require() is for ids ingest has already validated");
        assertFalse(registry.isKnown(FIRST), "isKnown is the question ingest asks instead");
    }

    @Test("A later closure does not retroactively close an earlier day")
    void closureIsKnowledgeDependent() {
        AccountRegistry registry = new AccountRegistry();
        registry.open(FIRST, AED, BusinessDay.of(1));
        registry.close(FIRST, BusinessDay.of(4));

        assertEquals(AccountState.OPEN, registry.stateOn(FIRST, BusinessDay.of(3)),
                "On day three the account had not yet closed");
        assertEquals(AccountState.CLOSED, registry.stateOn(FIRST, BusinessDay.of(4)),
                "It closes on the day of the closure, not the day after");
        assertEquals(AccountState.CLOSED, registry.stateOn(FIRST, BusinessDay.of(5)),
                "And stays closed");
    }

    @Test("Closing twice throws; there is no second closure to record")
    void doubleCloseThrows() {
        AccountRegistry registry = new AccountRegistry();
        registry.open(FIRST, AED, BusinessDay.of(1));
        registry.close(FIRST, BusinessDay.of(4));

        assertThrows(IllegalStateException.class, () -> registry.close(FIRST, BusinessDay.of(5)),
                "A second close should throw");
    }

    @Test("An account opened mid-window records the day it opened")
    void openingDayIsRecorded() {
        AccountRegistry registry = new AccountRegistry();
        Account account = registry.open(FIRST, AED, BusinessDay.of(3));

        assertEquals(BusinessDay.of(3), account.openedOn(), "Opening day should be kept");
        assertTrue(registry.find(FIRST).isPresent(), "The account should be findable");
    }
}
