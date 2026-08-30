package com.accountledger.engine;

import com.accountledger.account.Account;
import com.accountledger.account.AccountId;
import com.accountledger.account.AccountRegistry;
import com.accountledger.account.AccountState;
import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerBook;
import com.accountledger.event.AuthorizationId;
import com.accountledger.event.EventId;
import com.accountledger.event.LedgerEvent;
import com.accountledger.hold.Hold;
import com.accountledger.hold.HoldRegistry;
import com.accountledger.money.Money;
import com.accountledger.outcome.Outcome;
import com.accountledger.policy.FeeAssessment;
import com.accountledger.policy.FeeContext;
import com.accountledger.policy.InterestAccrualPolicy;
import com.accountledger.report.AccountDayReport;
import com.accountledger.report.AuthorizationStatus;
import com.accountledger.report.DayReport;
import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closes one day, in an order that is fixed and not open to rearrangement.
 *
 * <p>{@link #close} is {@code final} on purpose. The stages are separately overridable, so a
 * different fee rule or a different accrual is a subclass away, but their sequence is not: it
 * decides the answers. Assessing the fee before the day's instructions are processed would test
 * a balance that has not happened yet. Accruing interest before the fee is booked would pay
 * interest on money that has already been taken. Both are silent errors, and both are
 * unavailable here.
 *
 * <p>The order is: process every instruction scheduled for the day, snapshot each closing
 * balance, assess and book fees against that snapshot, then accrue interest on what is left.
 */
public class DayCloseProcessor {

    private final LedgerConfig config;
    private final AccountRegistry accounts;
    private final LedgerBook book;
    private final HoldRegistry holds;
    private final Replayer replayer;
    private final InterestAccrualPolicy interestPolicy;

    public DayCloseProcessor(LedgerConfig config, AccountRegistry accounts, LedgerBook book,
            HoldRegistry holds, Replayer replayer, InterestAccrualPolicy interestPolicy) {
        this.config = config;
        this.accounts = accounts;
        this.book = book;
        this.holds = holds;
        this.replayer = replayer;
        this.interestPolicy = interestPolicy;
    }

    /** The stage order. Final: the sequence is the rule, not a detail of this implementation. */
    public final DayReport close(BusinessDay day, List<LedgerEvent> scheduled) {
        List<Outcome> outcomes = processInstructions(day, scheduled);
        Map<AccountId, Money> beforeFees = snapshotClosingBalances(day);
        Map<AccountId, Money> fees = assessAndBookFees(day);
        Map<AccountId, Money> interest = accrueInterest(day);
        return buildReport(day, outcomes, beforeFees, fees, interest);
    }

    protected List<Outcome> processInstructions(BusinessDay day, List<LedgerEvent> scheduled) {
        List<Outcome> outcomes = new ArrayList<>();
        for (LedgerEvent event : scheduled) {
            outcomes.add(replayer.apply(event, day));
        }
        return outcomes;
    }

    /** The balance the fee rule is tested against: this day, as known on this day, pre-fee. */
    protected Map<AccountId, Money> snapshotClosingBalances(BusinessDay day) {
        Map<AccountId, Money> balances = new LinkedHashMap<>();
        for (Account account : accounts.all()) {
            balances.put(account.id(), book.balanceAsOf(account.id(), day, day));
        }
        return balances;
    }

    protected Map<AccountId, Money> assessAndBookFees(BusinessDay day) {
        Map<AccountId, Money> charged = new LinkedHashMap<>();
        for (Account account : accounts.all()) {
            Money total = Money.zero(account.currency());
            if (assessable(account, day)) {
                FeeContext context = new FeeContext(
                        account, day, config.windowStart(), config.windowEnd(), book);
                for (FeeAssessment fee : config.feePolicy().assess(context)) {
                    book.append(feeEventId(account.id(), day, fee.valueDay()), account.id(), EntryType.FEE,
                            fee.amount(), fee.valueDay(), day, null);
                    total = total.plus(fee.amount());
                }
            }
            charged.put(account.id(), total);
        }
        return charged;
    }

    /** Interest accrues on the balance that stands after any fee, never on the pre-fee one. */
    protected Map<AccountId, Money> accrueInterest(BusinessDay day) {
        Map<AccountId, Money> published = new LinkedHashMap<>();
        for (Account account : accounts.all()) {
            Money accrued = assessable(account, day)
                    ? interestPolicy.publish(account, book.balanceAsOf(account.id(), day, day))
                    : Money.zero(account.currency());
            published.put(account.id(), accrued);
        }
        return published;
    }

    protected DayReport buildReport(BusinessDay day, List<Outcome> outcomes,
            Map<AccountId, Money> beforeFees, Map<AccountId, Money> fees,
            Map<AccountId, Money> interest) {
        List<AccountDayReport> rows = new ArrayList<>();
        for (Account account : accounts.all()) {
            AccountId id = account.id();
            Money closing = book.balanceAsOf(id, day, day);
            Money held = holds.activeTotalFor(id, account.currency());
            rows.add(new AccountDayReport(
                    id,
                    accounts.stateOn(id, day),
                    beforeFees.get(id),
                    closing,
                    held,
                    closing.minus(held),
                    fees.get(id),
                    interest.get(id)));
        }
        return new DayReport(day, outcomes, rows, authorizationStatuses());
    }

    /** Every authorization seen so far, finished ones included, in approval order. */
    private List<AuthorizationStatus> authorizationStatuses() {
        List<AuthorizationStatus> statuses = new ArrayList<>();
        for (Account account : accounts.all()) {
            for (AuthorizationId id : holds.idsFor(account.id())) {
                Hold hold = holds.find(id).orElseThrow();
                statuses.add(new AuthorizationStatus(
                        id, account.id(), hold.amount(), holds.stateOf(id).orElseThrow()));
            }
        }
        return statuses;
    }

    /**
     * Whether a day's rules apply to an account at all. An account that has not opened yet has
     * no balance to charge or pay on, and a closed one is finished: charging an overdraft fee
     * to an account nobody can use any more would be a fee for the bank's own record-keeping.
     */
    private boolean assessable(Account account, BusinessDay day) {
        return !account.openedOn().isAfter(day)
                && accounts.stateOn(account.id(), day) == AccountState.OPEN;
    }

    /**
     * Fees are booked by the day close rather than by a submitted instruction, so they need
     * source ids of their own. The colon separator cannot collide with an id from a stream,
     * where it would have to survive being written by hand.
     *
     * <p>The value day is part of the id, not decoration. A policy that restates history books
     * several fees in one close, and without the value day they would all share an id and a
     * later reversal could not say which of them it meant.
     */
    private static EventId feeEventId(AccountId account, BusinessDay assessed, BusinessDay value) {
        return EventId.of("FEE:" + account + ":" + assessed.index() + ":" + value.index());
    }
}
