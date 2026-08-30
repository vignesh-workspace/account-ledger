package com.accountledger.engine;

import static com.accountledger.fixture.CanonicalStream.ACC_001;
import static com.accountledger.fixture.CanonicalStream.ACC_002;
import static com.accountledger.fixture.CanonicalStream.aed;
import static com.accountledger.fixture.CanonicalStream.bhd;
import static com.accountledger.testkit.Assert.assertEquals;
import static com.accountledger.testkit.Assert.assertTrue;

import com.accountledger.account.AccountId;
import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerEntry;
import com.accountledger.fixture.CanonicalStream;
import com.accountledger.money.Money;
import com.accountledger.outcome.Outcome;
import com.accountledger.outcome.Rejected;
import com.accountledger.outcome.RejectionReason;
import com.accountledger.report.AccountDayReport;
import com.accountledger.report.AccountSummary;
import com.accountledger.report.DayReport;
import com.accountledger.report.ReplayResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * Runs the scenario and reads the result, so the tests that assert the numbers can say what
 * they mean without restating how to find a row in a report.
 */
public final class ScenarioReplay {

    private ScenarioReplay() {}

    static ReplayResult replayScenario() {
        return new LedgerEngine(CanonicalStream.configuration().build())
                .replay(CanonicalStream.events());
    }

    static AccountDayReport row(ReplayResult result, int day, AccountId account) {
        DayReport report = result.days().get(day - 1);
        for (AccountDayReport candidate : report.accounts()) {
            if (candidate.account().equals(account)) {
                return candidate;
            }
        }
        throw new AssertionError("No row for " + account + " on day " + day);
    }

    static Outcome outcome(ReplayResult result, String eventId) {
        for (DayReport day : result.days()) {
            for (Outcome candidate : day.outcomes()) {
                if (candidate.eventId().value().equals(eventId)) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("No outcome for " + eventId);
    }

    static AccountSummary summary(ReplayResult result, AccountId account) {
        for (AccountSummary candidate : result.summaries()) {
            if (candidate.account().equals(account)) {
                return candidate;
            }
        }
        throw new AssertionError("No summary for " + account);
    }

    static RejectionReason reasonFor(ReplayResult result, String eventId) {
        Outcome found = outcome(result, eventId);
        if (found instanceof Rejected rejected) {
            return rejected.reason();
        }
        throw new AssertionError(eventId + " was accepted: " + found.describe());
    }

    /**
     * Re-derives a balance straight from the journal, with both days named.
     *
     * <p>Deliberately recomputed here rather than read off a report: a criterion about what day
     * two looked like on day five is a claim about the journal, and checking it against the
     * report would only prove the report agrees with itself.
     */
    static Money balanceAsOf(
            ReplayResult result, AccountId account, int valueDay, int knowledgeDay) {
        BigDecimal total = BigDecimal.ZERO;
        Money scale = null;
        for (LedgerEntry entry : result.journal()) {
            if (entry.accountId().equals(account)) {
                scale = entry.amount();
                if (entry.valueDay().index() <= valueDay && entry.bookingDay().index() <= knowledgeDay) {
                    total = total.add(entry.signedAmount());
                }
            }
        }
        if (scale == null) {
            throw new AssertionError("No entries at all for " + account);
        }
        return Money.round(total, scale.currency());
    }

    static long feeCount(ReplayResult result, AccountId account) {
        return result.journal().stream()
                .filter(entry -> entry.accountId().equals(account))
                .filter(entry -> entry.type() == EntryType.FEE)
                .count();
    }
}
