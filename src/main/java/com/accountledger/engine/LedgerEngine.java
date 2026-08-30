package com.accountledger.engine;

import com.accountledger.account.Account;
import com.accountledger.account.AccountRegistry;
import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerBook;
import com.accountledger.event.EventId;
import com.accountledger.event.LedgerEvent;
import com.accountledger.hold.HoldRegistry;
import com.accountledger.money.Money;
import com.accountledger.outcome.Outcome;
import com.accountledger.outcome.Rejected;
import com.accountledger.outcome.RejectionReason;
import com.accountledger.policy.InterestAccrualPolicy;
import com.accountledger.report.AccountSummary;
import com.accountledger.report.DayReport;
import com.accountledger.report.ReplayResult;
import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The way in. Hand it a stream, get back every day report, every account summary and the
 * journal that produced them.
 *
 * <p>Each call to {@link #replay} builds its own registries, book and interest policy and
 * throws them away afterwards. Nothing survives between replays, which is what lets the
 * determinism test run the same stream through two engines and compare whole results for
 * equality.
 */
public final class LedgerEngine {

    private final LedgerConfig config;

    public LedgerEngine(LedgerConfig config) {
        this.config = config;
    }

    public ReplayResult replay(List<LedgerEvent> events) {
        AccountRegistry accounts = new AccountRegistry();
        LedgerBook book = new LedgerBook(accounts);
        HoldRegistry holds = new HoldRegistry();
        InterestAccrualPolicy interestPolicy = config.newInterestPolicy();
        Replayer replayer = new Replayer(accounts, book, holds, config.decisionPolicy());
        DayCloseProcessor dayClose =
                new DayCloseProcessor(config, accounts, book, holds, replayer, interestPolicy);

        openAccounts(accounts, book);

        List<Outcome> ingestRejections = new ArrayList<>();
        Map<BusinessDay, List<LedgerEvent>> schedule = schedule(events, ingestRejections);

        List<DayReport> days = new ArrayList<>();
        for (BusinessDay day : config.window()) {
            days.add(dayClose.close(day, schedule.get(day)));
        }

        List<AccountSummary> summaries =
                capitaliseAndSummarise(accounts, book, interestPolicy);
        return new ReplayResult(days, ingestRejections, summaries, book.entries());
    }

    /**
     * Opening balances are entries, not fields. An account whose balance were a number on the
     * account object would need a setter, and append-only would become a convention rather than
     * a property. A zero opening still books, so the account's existence is visible in the
     * journal and the conservation check has nothing special to skip.
     */
    private void openAccounts(AccountRegistry accounts, LedgerBook book) {
        for (LedgerConfig.AccountOpening opening : config.openings()) {
            accounts.open(opening.id(), opening.currency(), opening.openedOn());
            book.append(EventId.of("OPEN:" + opening.id()), opening.id(), EntryType.OPENING,
                    opening.openingBalance(), opening.openedOn(), opening.openedOn(), null);
        }
    }

    /**
     * Decides which day each instruction is processed on. This is the only place the two
     * readings of a late arrival differ, and neither reading changes a single rule downstream.
     *
     * <p>Bucket-and-close, the default: an instruction is processed on the day it says it was
     * booked, whatever order the stream lists it in. The scenario needs this, since a reversal
     * booking on day six is written before a credit booking on day five.
     *
     * <p>Strict arrival order: the stream is a tape. Days close as the tape passes them, and an
     * instruction dated before the day currently open is refused as a late arrival. It is
     * scheduled on the day that was open when it turned up, so its refusal is reported where it
     * actually happened rather than on a day that had already finished.
     */
    private Map<BusinessDay, List<LedgerEvent>> schedule(
            List<LedgerEvent> events, List<Outcome> ingestRejections) {
        Map<BusinessDay, List<LedgerEvent>> schedule = new LinkedHashMap<>();
        for (BusinessDay day : config.window()) {
            schedule.put(day, new ArrayList<>());
        }
        BusinessDay open = config.windowStart();
        for (LedgerEvent event : events) {
            BusinessDay booking = event.bookingDay();
            if (!config.isInWindow(booking)) {
                // Belongs to no day in the window, so no day report could honestly carry it.
                ingestRejections.add(Rejected.of(event.eventId(),
                        RejectionReason.DAY_OUTSIDE_WINDOW,
                        booking, config.windowStart(), config.windowEnd()));
                continue;
            }
            BusinessDay processOn = booking;
            if (config.strictArrivalOrder()) {
                if (booking.isAfter(open)) {
                    open = booking;
                }
                processOn = open;
            }
            schedule.get(processOn).add(event);
        }
        return schedule;
    }

    /**
     * Publishes the accrued interest as a single credit at the end of the window and reports
     * where every account finished.
     *
     * <p>Capitalisation happens after the last day's report is built, so the final day closes
     * on its own trading and the interest credit is visible as the separate thing it is. The
     * summary carries the closing balance, the capitalised amount and the final balance, and
     * the three have to reconcile in plain sight.
     */
    private List<AccountSummary> capitaliseAndSummarise(
            AccountRegistry accounts, LedgerBook book, InterestAccrualPolicy interestPolicy) {
        BusinessDay last = config.windowEnd();
        List<AccountSummary> summaries = new ArrayList<>();
        for (Account account : accounts.all()) {
            Money closing = book.balanceAsOf(account.id(), last, last);
            Money capitalised = interestPolicy.publishedTotal(account);
            if (capitalised.isPositive()) {
                book.append(EventId.of("INT:" + account.id()), account.id(), EntryType.INTEREST,
                        capitalised, last, last, null);
            }
            summaries.add(new AccountSummary(account.id(), accounts.stateOn(account.id(), last),
                    closing, capitalised, book.balanceAsOf(account.id(), last, last)));
        }
        return summaries;
    }
}
