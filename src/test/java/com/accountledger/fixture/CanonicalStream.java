package com.accountledger.fixture;

import com.accountledger.account.AccountId;
import com.accountledger.engine.LedgerConfig;
import com.accountledger.event.EventStream;
import com.accountledger.event.LedgerEvent;
import com.accountledger.money.Money;
import java.math.BigDecimal;
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

    /**
     * The rules as given, expressed as configuration rather than as code.
     *
     * <p>A builder rather than a finished config, so a test that wants to vary one thing — a
     * different fee policy, strict arrival order — changes that one thing and inherits the rest
     * of the scenario instead of restating it and drifting away from it.
     */
    public static LedgerConfig.Builder configuration() {
        return LedgerConfig.builder()
                .window(1, 6)
                .account(ACC_001, AED, 1, aed("0.00"))
                .account(ACC_002, BHD, 1, bhd("0.000"))
                .overdraftFee(aed("25.00"))
                .dailyInterestRate(DAILY_INTEREST_RATE);
    }

    /** 0.04% per day, as given. Not a rate the engine believes in; a number it was handed. */
    public static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.0004");

    /**
     * The same scenario with one instruction moved: the second authorization is judged before
     * the backdated debit rather than after it.
     *
     * <p>Nothing else changes — same events, same amounts, same booking and value days — so any
     * difference in the result is attributable to order alone. It lives here with the stream it
     * varies, because it is scenario data and this is the only place scenario data exists.
     */
    public static List<LedgerEvent> eventsWithAuthorizationJudgedFirst() {
        return EventStream.builder()
                .credit("E1", 1, ACC_001, aed("1200.00"), 1)
                .debit("E2", 1, ACC_001, aed("950.00"), 1)
                .authorization("E3", 2, ACC_001, "Auth-A", aed("200.00"), 2)
                .credit("E4", 3, ACC_001, aed("400.00"), 3)
                .settlement("E5", 4, ACC_001, "Auth-A", aed("185.00"), 4)
                .settlement("E6", 4, ACC_001, "Auth-Z", aed("180.00"), 4)
                .authorization("E8", 5, ACC_001, "Auth-B", aed("90.00"), 5)
                .debit("E7", 5, ACC_001, aed("620.00"), 2)
                .reversal("E9", 6, ACC_001, "E7", 2)
                .creditInInstalments("E10", 5, ACC_002, bhd("10.000"), 3, 5)
                .build();
    }
}
