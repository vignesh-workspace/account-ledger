package com.accountledger;

import com.accountledger.book.LedgerEntry;
import com.accountledger.engine.LedgerEngine;
import com.accountledger.fixture.CanonicalStream;
import com.accountledger.report.AccountSummary;
import com.accountledger.report.ReplayResult;
import com.accountledger.report.ReportPrinter;
import java.math.BigDecimal;

/**
 * Replays the scenario and prints it.
 *
 * <p>Lives beside the scenario rather than in the engine, and that placement is the same rule
 * the engine is held to: an account number belongs to a fixture, never to the ledger core.
 * <pre>grep -rn "ACC-00\|Auth-[ABZ]" src/main/java   # returns nothing</pre>
 *
 * <p>Exits non-zero if the ledger fails to reconcile against its own entries, so the harness is
 * usable as a check and not only as something to read.
 */
public final class LedgerHarness {

    private LedgerHarness() {}

    public static void main(String[] args) {
        ReplayResult result = new LedgerEngine(CanonicalStream.configuration().build())
                .replay(CanonicalStream.events());
        new ReportPrinter(System.out).print(result);
        System.exit(reconciles(result) ? 0 : 1);
    }

    /**
     * Every account's entries must sum to its final balance.
     *
     * <p>Entries here are single-sided: a credit of 400 has no matching debit anywhere, so the
     * ledger as a whole does not sum to zero and cannot be checked by double entry. This is the
     * substitute, and it catches the failure that matters most — money appearing that no entry
     * accounts for.
     */
    private static boolean reconciles(ReplayResult result) {
        for (AccountSummary summary : result.summaries()) {
            BigDecimal journalled = BigDecimal.ZERO;
            for (LedgerEntry entry : result.journal()) {
                if (entry.accountId().equals(summary.account())) {
                    journalled = journalled.add(entry.signedAmount());
                }
            }
            if (journalled.compareTo(summary.finalBalance().amount()) != 0) {
                return false;
            }
        }
        return true;
    }
}
