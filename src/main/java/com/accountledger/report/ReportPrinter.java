package com.accountledger.report;

import com.accountledger.book.LedgerEntry;
import com.accountledger.outcome.Outcome;
import java.io.PrintStream;
import java.util.List;

/**
 * Renders a replay result as text.
 *
 * <p>Printing is kept out of the engine entirely. Everything here reads a finished
 * {@link ReplayResult} and writes to a stream; nothing it does can change an answer, which is
 * what lets the determinism test compare two results without any of this running at all.
 *
 * <p>Amounts are right-aligned in fixed columns so a reader can scan a column of figures. The
 * width is generous rather than computed: computing it from the data would make the layout of
 * one day depend on the contents of another.
 */
public final class ReportPrinter {

    private static final int MONEY_WIDTH = 14;
    private static final String RULE = "=".repeat(78);
    private static final String THIN = "-".repeat(78);

    private final PrintStream out;

    public ReportPrinter(PrintStream out) {
        this.out = out;
    }

    public void print(ReplayResult result) {
        out.println(RULE);
        out.println("LEDGER REPLAY");
        out.println(RULE);

        if (!result.ingestRejections().isEmpty()) {
            out.println();
            out.println("Refused before the window opened");
            for (Outcome refused : result.ingestRejections()) {
                out.println("  " + refused.describe());
            }
        }

        for (DayReport day : result.days()) {
            printDay(day);
        }
        printSummaries(result.summaries());
        printConservation(result);
    }

    private void printDay(DayReport day) {
        out.println();
        out.println(THIN);
        out.println(day.day());
        out.println(THIN);

        out.println("  Instructions");
        if (day.outcomes().isEmpty()) {
            out.println("    (none booked on this day)");
        }
        for (Outcome outcome : day.outcomes()) {
            out.println("    " + outcome.describe());
        }

        out.println("  Positions");
        out.printf("    %-12s %14s %14s %14s %10s %10s%n",
                "account", "closing", "holds", "available", "fee", "interest");
        for (AccountDayReport account : day.accounts()) {
            out.printf("    %-12s %14s %14s %14s %10s %10s%n",
                    account.account(),
                    account.closing().amount().toPlainString(),
                    account.holds().amount().toPlainString(),
                    account.available().amount().toPlainString(),
                    account.feesCharged().amount().toPlainString(),
                    account.interestPublished().amount().toPlainString());
            if (!account.closingBeforeFees().equals(account.closing())) {
                out.printf("    %-12s closing before fees %s, after %s%n",
                        "", account.closingBeforeFees(), account.closing());
            }
        }

        if (!day.authorizations().isEmpty()) {
            out.println("  Authorizations");
            for (AuthorizationStatus status : day.authorizations()) {
                out.printf("    %-12s %-10s %14s  %s%n",
                        status.id(), status.account(),
                        status.amount().amount().toPlainString(), status.state());
            }
        }
    }

    private void printSummaries(List<AccountSummary> summaries) {
        out.println();
        out.println(RULE);
        out.println("END OF WINDOW");
        out.println(RULE);
        out.printf("  %-12s %-8s %14s %14s %14s%n",
                "account", "state", "closing", "capitalised", "final");
        for (AccountSummary summary : summaries) {
            out.printf("  %-12s %-8s %14s %14s %14s%n",
                    summary.account(),
                    summary.state(),
                    summary.closingBeforeCapitalisation().amount().toPlainString(),
                    summary.capitalisedInterest().amount().toPlainString(),
                    summary.finalBalance().amount().toPlainString());
        }
        out.println();
        out.println("  Capitalisation is applied after the final day closes, so the closing");
        out.println("  column is that day's own trading and the final column includes interest.");
        out.println("  A published daily accrual is not always that day's balance times the rate:");
        out.println("  the remainder carries between days so the daily figures sum exactly to the");
        out.println("  capitalised total.");
    }

    /**
     * The signed sum of every entry, against the sum of the final balances.
     *
     * <p>Entries are single-sided and there is no contra account, so nothing in this ledger
     * structurally nets to zero. That makes this the only guard against a bug that creates
     * money out of nothing, and it is ten lines.
     */
    private void printConservation(ReplayResult result) {
        out.println();
        out.println("  Conservation");
        boolean allAgree = true;
        for (AccountSummary summary : result.summaries()) {
            java.math.BigDecimal journalled = java.math.BigDecimal.ZERO;
            for (LedgerEntry entry : result.journal()) {
                if (entry.accountId().equals(summary.account())) {
                    journalled = journalled.add(entry.signedAmount());
                }
            }
            boolean agrees = journalled.compareTo(summary.finalBalance().amount()) == 0;
            allAgree &= agrees;
            out.printf("    %-12s entries sum to %s, final balance %s  %s%n",
                    summary.account(), journalled.toPlainString(),
                    summary.finalBalance().amount().toPlainString(), agrees ? "ok" : "MISMATCH");
        }
        out.println("    " + (allAgree
                ? "every account reconciles against its own entries"
                : "AT LEAST ONE ACCOUNT DOES NOT RECONCILE"));
    }
}
