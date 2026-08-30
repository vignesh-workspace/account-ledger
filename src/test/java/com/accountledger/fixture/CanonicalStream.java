package com.accountledger.fixture;

import com.accountledger.account.AccountId;
import com.accountledger.event.EventStream;
import com.accountledger.event.LedgerEvent;
import com.accountledger.money.Money;
import java.util.Currency;
import java.util.List;

/**
 * The scenario under assessment, expressed as data.
 *
 * <p>This class is the only place in the project where a specific account number, event
 * identifier or authorization name appears. The engine has no knowledge of any of them:
 *
 * <pre>grep -r "ACC-001\|Auth-A\|E7" src/main/java   # returns nothing</pre>
 *
 * <p>If that command ever produces output, the scenario has leaked into the engine and the
 * engine has stopped being general.
 */
public final class CanonicalStream {

    public static final Currency AED = Currency.getInstance("AED");
    public static final Currency BHD = Currency.getInstance("BHD");

    public static final AccountId ACC_001 = AccountId.of("ACC-001");
    public static final AccountId ACC_002 = AccountId.of("ACC-002");

    private CanonicalStream() {}

    public static Money aed(String amount) {
        return Money.of(amount, AED);
    }

    public static Money bhd(String amount) {
        return Money.of(amount, BHD);
    }

    /**
     * Events in the order given by the specification.
     *
     * <p>Two things to notice, both of which the engine has to have an answer for.
     *
     * <p>The list is not sorted by booking day. The reversal books on day six while the
     * instalment credit books on day five, yet the credit is written last. So a stream is an
     * unordered set of dated instructions, not a chronological log, and the engine needs a
     * stated rule for an instruction that arrives after a later day has already closed.
     *
     * <p>One debit books three days after the day it takes value from. It is the reason a
     * balance that was reported once can be a different number when asked again later, and
     * the reason the closing balance of a day is meaningless without saying when the question
     * is being asked.
     */
    public static List<LedgerEvent> events() {
        return EventStream.builder()
                .credit("E1", 1, ACC_001, aed("1200.00"), 1)
                .debit("E2", 1, ACC_001, aed("950.00"), 1)
                .authorization("E3", 2, ACC_001, "Auth-A", aed("200.00"), 2)
                .credit("E4", 3, ACC_001, aed("400.00"), 3)
                .settlement("E5", 4, ACC_001, "Auth-A", aed("185.00"), 4)
                // Settles an authorization that was never requested.
                .settlement("E6", 4, ACC_001, "Auth-Z", aed("180.00"), 4)
                // Books on day five, takes value from day two.
                .debit("E7", 5, ACC_001, aed("620.00"), 2)
                .authorization("E8", 5, ACC_001, "Auth-B", aed("90.00"), 5)
                .reversal("E9", 6, ACC_001, "E7", 2)
                // Ten in a three-decimal currency does not divide by three.
                .creditInInstalments("E10", 5, ACC_002, bhd("10.000"), 3, 5)
                .build();
    }
}
