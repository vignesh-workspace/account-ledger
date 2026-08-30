package com.accountledger.policy;

import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertThrows;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.account.Account;
import com.accountledger.account.AccountId;
import com.accountledger.account.AccountRegistry;
import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerBook;
import com.accountledger.event.EventId;
import com.accountledger.money.Money;
import com.accountledger.testkit.Test;
import com.accountledger.time.BusinessDay;
import java.util.Currency;
import java.util.List;
import java.util.Map;

public class FlatOverdraftFeePolicyTest {

    private static final Currency AED = Currency.getInstance("AED");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final AccountId ID = AccountId.of("charged-account");

    private final AccountRegistry registry = new AccountRegistry();
    private final Account account;
    private final LedgerBook book;
    private final OverdraftFeePolicy policy =
            new FlatOverdraftFeePolicy(Map.of(AED, Money.of("25.00", AED)));

    public FlatOverdraftFeePolicyTest() {
        account = registry.open(ID, AED, BusinessDay.of(1));
        book = new LedgerBook(registry);
    }

    private static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    private void post(String event, EntryType type, String amount, int valueDay, int bookingDay) {
        book.append(EventId.of(event), ID, type, aed(amount),
                BusinessDay.of(valueDay), BusinessDay.of(bookingDay), null);
    }

    private List<FeeAssessment> assessOn(int day) {
        return policy.assess(new FeeContext(account, BusinessDay.of(day),
                BusinessDay.of(1), BusinessDay.of(6), book));
    }

    @Test("A negative closing balance draws one fee, valued on the day assessed")
    void oneFeeOnTheDayAssessed() {
        post("out", EntryType.DEBIT, "155.00", 5, 5);
        List<FeeAssessment> fees = assessOn(5);

        assertEquals(1, fees.size(), "Once per day per account");
        assertEquals(aed("25.00"), fees.get(0).amount(), "The configured fee");
        assertEquals(BusinessDay.of(5), fees.get(0).valueDay(),
                "Value date equals the day assessed, as the rule states");
    }

    @Test("Zero draws no fee: zero is not negative")
    void zeroIsNotOverdrawn() {
        post("in", EntryType.CREDIT, "100.00", 1, 1);
        post("out", EntryType.DEBIT, "100.00", 1, 1);

        assertTrue(assessOn(1).isEmpty(), "A balance of exactly zero is not overdrawn");
    }

    @Test("A positive balance draws no fee")
    void positiveDrawsNothing() {
        post("in", EntryType.CREDIT, "100.00", 1, 1);
        assertTrue(assessOn(1).isEmpty(), "Nothing to charge");
    }

    @Test("A day that closed clean is not reopened when a backdated entry restates it")
    void closedDaysAreNotRevisited() {
        post("in", EntryType.CREDIT, "250.00", 1, 1);
        // Books on day five, takes value from day two: the day two view is now negative.
        post("backdated", EntryType.DEBIT, "620.00", 2, 5);

        assertEquals(aed("-370.00"), book.balanceAsOf(ID, BusinessDay.of(2), BusinessDay.of(5)),
                "Day two re-derives negative once the backdated debit is known");
        assertTrue(assessOn(2).isEmpty(),
                "But assessing day two on day two sees 250.00 and charges nothing");
        assertEquals(1, assessOn(5).size(),
                "The fee lands on day five, whose own closing balance is negative");
    }

    @Test("The fee is assessed against the balance before any fee is booked")
    void assessmentReadsThePreFeeBalance() {
        post("out", EntryType.DEBIT, "155.00", 5, 5);
        assertEquals(1, assessOn(5).size(), "One fee due");

        // Booking it must not make a second assessment of the same day look due twice.
        post("fee", EntryType.FEE, "25.00", 5, 5);
        assertEquals(aed("-180.00"), book.balanceAsOf(ID, BusinessDay.of(5), BusinessDay.of(5)),
                "The post-fee balance is what interest will read");
    }

    @Test("A fee falling due in an unconfigured currency stops rather than inventing a rate")
    void unconfiguredCurrencyThrows() {
        AccountRegistry other = new AccountRegistry();
        Account dinar = other.open(AccountId.of("dinar-account"), BHD, BusinessDay.of(1));
        LedgerBook dinarBook = new LedgerBook(other);
        dinarBook.append(EventId.of("out"), dinar.id(), EntryType.DEBIT, Money.of("1.000", BHD),
                BusinessDay.of(1), BusinessDay.of(1), null);

        assertThrows(IllegalStateException.class,
                () -> policy.assess(new FeeContext(dinar, BusinessDay.of(1),
                        BusinessDay.of(1), BusinessDay.of(6), dinarBook)),
                "Twenty-five dirhams is not twenty-five dinars, and there is no rate here");
    }
}
