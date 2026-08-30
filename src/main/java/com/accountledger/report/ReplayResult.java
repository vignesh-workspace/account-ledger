package com.accountledger.report;

import com.accountledger.book.LedgerEntry;
import com.accountledger.outcome.Outcome;
import java.util.List;

/**
 * The whole outcome of one replay: every day, every account, and the journal that produced
 * them.
 *
 * <p>{@code ingestRejections} holds instructions that belong to no day — an event booked
 * outside the replay window has no day whose report could carry it — so they are listed
 * separately rather than being quietly attached to a day they were never part of.
 *
 * <p>The journal is exposed so a caller can check the ledger against itself: the signed sum of
 * every entry has to equal the sum of the final balances. Given entries are single-sided and
 * nothing structurally nets to zero, that check is the only guard against a bug that creates
 * money.
 */
public record ReplayResult(
        List<DayReport> days,
        List<Outcome> ingestRejections,
        List<AccountSummary> summaries,
        List<LedgerEntry> journal) {

    public ReplayResult {
        days = List.copyOf(days);
        ingestRejections = List.copyOf(ingestRejections);
        summaries = List.copyOf(summaries);
        journal = List.copyOf(journal);
    }
}
