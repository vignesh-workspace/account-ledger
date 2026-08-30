package com.accountledger.engine;

import com.accountledger.account.Account;
import com.accountledger.account.AccountRegistry;
import com.accountledger.account.AccountState;
import com.accountledger.book.EntryType;
import com.accountledger.book.LedgerBook;
import com.accountledger.book.LedgerEntry;
import com.accountledger.event.AccountClosure;
import com.accountledger.event.Authorization;
import com.accountledger.event.AuthorizationId;
import com.accountledger.event.Credit;
import com.accountledger.event.Debit;
import com.accountledger.event.EventId;
import com.accountledger.event.LedgerEvent;
import com.accountledger.event.Reversal;
import com.accountledger.event.Settlement;
import com.accountledger.hold.AuthState;
import com.accountledger.hold.Hold;
import com.accountledger.hold.HoldRegistry;
import com.accountledger.money.Money;
import com.accountledger.outcome.Accepted;
import com.accountledger.outcome.Outcome;
import com.accountledger.outcome.Rejected;
import com.accountledger.outcome.RejectionReason;
import com.accountledger.policy.AuthorizationDecisionPolicy;
import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The fold. One instruction goes in against the state as it stands, one outcome comes out and
 * the state has moved on.
 *
 * <p>There is no clock here and no input or output. Everything a decision depends on is either
 * the event or state built from earlier events, which is why replaying the same stream twice
 * produces the same answers, and why there is a test that does exactly that.
 *
 * <p>Dispatch is an exhaustive switch over the sealed event interface. A seventh event kind
 * would not compile until it was handled here, which is the guarantee a visitor would have
 * bought at the cost of double dispatch.
 *
 * <p>The order of the checks before the switch is deliberate. A duplicate id is caught first,
 * so a repeated instruction is refused for being a repeat rather than for whatever else is
 * wrong with it the second time around.
 */
public final class Replayer {

    private final AccountRegistry accounts;
    private final LedgerBook book;
    private final HoldRegistry holds;
    private final AuthorizationDecisionPolicy decisionPolicy;

    private final Set<EventId> seen = new LinkedHashSet<>();
    private final Map<EventId, AuthorizationId> authorizationsByEvent = new LinkedHashMap<>();

    public Replayer(AccountRegistry accounts, LedgerBook book, HoldRegistry holds,
            AuthorizationDecisionPolicy decisionPolicy) {
        this.accounts = accounts;
        this.book = book;
        this.holds = holds;
        this.decisionPolicy = decisionPolicy;
    }

    /**
     * Applies one instruction on {@code processingDay}.
     *
     * <p>The processing day is a parameter rather than something read off the event, because
     * the two are not always equal and their disagreement is exactly what a late arrival is.
     * Under the default reading they match by construction; under strict arrival order an
     * instruction dated before the day currently open is refused, and that single comparison
     * is the whole difference between the two readings.
     */
    public Outcome apply(LedgerEvent event, BusinessDay processingDay) {
        EventId id = event.eventId();
        if (!seen.add(id)) {
            return Rejected.of(id, RejectionReason.DUPLICATE_EVENT_ID, id);
        }
        if (!accounts.isKnown(event.accountId())) {
            return Rejected.of(id, RejectionReason.UNKNOWN_ACCOUNT, event.accountId());
        }
        if (event.bookingDay().isBefore(processingDay)) {
            return Rejected.of(id, RejectionReason.LATE_ARRIVAL_TO_CLOSED_DAY,
                    event.bookingDay(), processingDay);
        }
        Account account = accounts.require(event.accountId());
        if (account.openedOn().isAfter(event.bookingDay())) {
            return Rejected.of(id, RejectionReason.ACCOUNT_NOT_YET_OPEN,
                    account.id(), account.openedOn());
        }
        if (!(event instanceof AccountClosure)
                && accounts.stateOn(account.id(), processingDay) == AccountState.CLOSED) {
            return Rejected.of(id, RejectionReason.ACCOUNT_CLOSED, account.id(),
                    accounts.closureDay(account.id()).orElseThrow());
        }

        return switch (event) {
            case Credit credit -> book(credit, account, EntryType.CREDIT, credit.amount());
            case Debit debit -> book(debit, account, EntryType.DEBIT, debit.amount());
            case Authorization authorization -> judge(authorization, account, processingDay);
            case Settlement settlement -> settle(settlement, account);
            case Reversal reversal -> reverse(reversal, account);
            case AccountClosure closure -> closeAccount(closure, account, processingDay);
        };
    }

    private Outcome book(LedgerEvent event, Account account, EntryType type, Money amount) {
        book.append(event.eventId(), account.id(), type, amount,
                event.valueDay(), event.bookingDay(), null);
        return new Accepted(event.eventId(), type + " " + amount + " value " + event.valueDay());
    }

    private Outcome judge(Authorization authorization, Account account, BusinessDay processingDay) {
        AuthorizationId authorizationId = authorization.authorizationId();
        if (holds.stateOf(authorizationId).isPresent()) {
            return Rejected.of(authorization.eventId(),
                    RejectionReason.DUPLICATE_AUTHORIZATION_ID, authorizationId);
        }
        // The balance as known today, not as it will later be restated. An authorization is
        // judged once, against what the ledger actually said at the moment it was asked.
        Money ledgerBalance = book.balanceAsOf(account.id(), processingDay, processingDay);
        Money activeHolds = holds.activeTotalFor(account.id(), account.currency());
        Money available = ledgerBalance.minus(activeHolds);

        if (!decisionPolicy.approves(ledgerBalance, activeHolds, authorization.amount())) {
            return Rejected.of(authorization.eventId(),
                    RejectionReason.INSUFFICIENT_AVAILABLE_BALANCE,
                    available, available.minus(authorization.amount()), authorization.amount());
        }
        holds.place(new Hold(authorizationId, account.id(), authorization.amount(),
                authorization.bookingDay()));
        authorizationsByEvent.put(authorization.eventId(), authorizationId);
        return new Accepted(authorization.eventId(),
                "approved " + authorizationId + ", holding " + authorization.amount()
                        + ", available " + available.minus(authorization.amount()));
    }

    private Outcome settle(Settlement settlement, Account account) {
        AuthorizationId authorizationId = settlement.authorizationId();
        Optional<Hold> hold = holds.find(authorizationId);
        if (hold.isEmpty()) {
            return Rejected.of(settlement.eventId(),
                    RejectionReason.UNKNOWN_AUTHORIZATION, authorizationId);
        }
        AuthState state = holds.stateOf(authorizationId).orElseThrow();
        if (state != AuthState.ACTIVE) {
            return Rejected.of(settlement.eventId(),
                    RejectionReason.AUTHORIZATION_ALREADY_CLOSED, authorizationId, state);
        }
        book.append(settlement.eventId(), account.id(), EntryType.DEBIT, settlement.amount(),
                settlement.valueDay(), settlement.bookingDay(), null);

        // A partial settlement closes the authorization and gives the difference back rather
        // than leaving it reserved. The merchant has said what the transaction was worth;
        // holding the rest would restrict funds against a completed instruction.
        holds.close(authorizationId, AuthState.SETTLED);
        Money remainder = hold.orElseThrow().amount().minus(settlement.amount());
        String released;
        if (remainder.isZero()) {
            released = "";
        } else if (remainder.isNegative()) {
            released = ", exceeding the hold by " + remainder.negated();
        } else {
            released = ", releasing " + remainder;
        }
        return new Accepted(settlement.eventId(),
                "settled " + settlement.amount() + " against " + authorizationId + released);
    }

    private Outcome reverse(Reversal reversal, Account account) {
        // Reversing the authorization itself, which booked no entry, releases the hold.
        AuthorizationId authorizationId = authorizationsByEvent.get(reversal.reversedEventId());
        if (authorizationId != null) {
            AuthState state = holds.stateOf(authorizationId).orElseThrow();
            if (state != AuthState.ACTIVE) {
                return Rejected.of(reversal.eventId(),
                        RejectionReason.AUTHORIZATION_ALREADY_CLOSED, authorizationId, state);
            }
            holds.close(authorizationId, AuthState.REVERSED);
            return new Accepted(reversal.eventId(),
                    "reversed " + authorizationId + ", hold released");
        }

        Optional<LedgerEntry> original = book.findBySourceEvent(reversal.reversedEventId());
        if (original.isEmpty()) {
            return Rejected.of(reversal.eventId(),
                    RejectionReason.UNKNOWN_ENTRY, reversal.reversedEventId());
        }
        LedgerEntry entry = original.orElseThrow();
        if (book.isReversed(entry.sequence())) {
            return Rejected.of(reversal.eventId(),
                    RejectionReason.ALREADY_REVERSED, entry.sequence());
        }
        // The opposite direction, derived from the original rather than declared on the event:
        // reversing a debit really is a credit, and deriving it keeps the two from disagreeing.
        EntryType compensating = entry.type().signum() < 0 ? EntryType.CREDIT : EntryType.DEBIT;
        book.append(reversal.eventId(), account.id(), compensating, entry.amount(),
                reversal.valueDay(), reversal.bookingDay(), entry.sequence());
        return new Accepted(reversal.eventId(),
                "reversed entry #" + entry.sequence() + " as " + compensating + " "
                        + entry.amount() + " value " + reversal.valueDay());
    }

    private Outcome closeAccount(
            AccountClosure closure, Account account, BusinessDay processingDay) {
        if (accounts.stateOn(account.id(), processingDay) == AccountState.CLOSED) {
            return Rejected.of(closure.eventId(), RejectionReason.ACCOUNT_ALREADY_CLOSED,
                    account.id(), accounts.closureDay(account.id()).orElseThrow());
        }
        // A live hold cannot outlive its account. Released rather than settled is the honest
        // terminal state: no settlement arrived, and the funds are simply no longer reserved.
        List<AuthorizationId> released = new ArrayList<>();
        for (AuthorizationId authorizationId : holds.idsFor(account.id())) {
            if (holds.isActive(authorizationId)) {
                holds.close(authorizationId, AuthState.RELEASED);
                released.add(authorizationId);
            }
        }
        accounts.close(account.id(), processingDay);
        return new Accepted(closure.eventId(), released.isEmpty()
                ? "closed " + account.id()
                : "closed " + account.id() + ", releasing holds " + released);
    }
}
