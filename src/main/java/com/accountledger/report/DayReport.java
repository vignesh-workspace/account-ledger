package com.accountledger.report;

import com.accountledger.outcome.Outcome;
import com.accountledger.time.BusinessDay;
import java.util.List;

/**
 * Everything that happened on one day: what was instructed and what became of it, then where
 * each account stood once the day closed.
 *
 * <p>Every event scheduled for the day appears in {@code outcomes}, accepted or refused. A day
 * on which three instructions were declined is not an empty day, and a report that printed
 * only successes would say it was.
 */
public record DayReport(
        BusinessDay day,
        List<Outcome> outcomes,
        List<AccountDayReport> accounts,
        List<AuthorizationStatus> authorizations) {

    public DayReport {
        outcomes = List.copyOf(outcomes);
        accounts = List.copyOf(accounts);
        authorizations = List.copyOf(authorizations);
    }
}
