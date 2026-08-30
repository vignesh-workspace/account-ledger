package com.accountledger.hold;

import com.accountledger.account.AccountId;
import com.accountledger.event.AuthorizationId;
import com.accountledger.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every authorization the replay has seen, and what became of it.
 *
 * <p>Unlike the ledger book this registry is <em>not</em> bitemporal. An authorization's state
 * is only ever asked about at the moment an instruction is being judged — "is there still a
 * hold against this account right now" — and never as of a past knowledge day. Adding
 * knowledge-day queries here would be machinery with no caller, so the shape is honest about
 * what is actually asked. It is still a projection: discard it, replay the journal, and it
 * rebuilds identically.
 *
 * <p>An unknown authorization returns {@link Optional#empty()} rather than throwing. A
 * settlement quoting an id that was never approved is an ordinary thing for a card scheme to
 * send, so it has to become a rejection in the day report, not an exception.
 *
 * <p>{@link LinkedHashMap} again, for the same reason as the account registry: the report
 * lists authorization states, and that list must not depend on hash order.
 */
public final class HoldRegistry {

    private final Map<AuthorizationId, Hold> holds = new LinkedHashMap<>();
    private final Map<AuthorizationId, AuthState> states = new LinkedHashMap<>();

    /** Records an approved authorization and starts holding its funds. */
    public void place(Hold hold) {
        if (holds.containsKey(hold.id())) {
            throw new IllegalStateException(
                    "Authorization " + hold.id() + " has already been placed. "
                            + "Ingest validation should have rejected this event.");
        }
        holds.put(hold.id(), hold);
        states.put(hold.id(), AuthState.ACTIVE);
    }

    public Optional<Hold> find(AuthorizationId id) {
        return Optional.ofNullable(holds.get(id));
    }

    /** The state of a known authorization, or empty if it was never approved. */
    public Optional<AuthState> stateOf(AuthorizationId id) {
        return Optional.ofNullable(states.get(id));
    }

    public boolean isActive(AuthorizationId id) {
        return states.get(id) == AuthState.ACTIVE;
    }

    /**
     * Moves an authorization out of ACTIVE. Called for every way one can end; the caller
     * chooses which, because the terminal states are not interchangeable in a report.
     */
    public void close(AuthorizationId id, AuthState terminal) {
        if (terminal == AuthState.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE is not a terminal state");
        }
        if (!states.containsKey(id)) {
            throw new IllegalStateException("Unknown authorization: " + id);
        }
        states.put(id, terminal);
    }

    /**
     * The total still held against an account. Needs the currency so that an account with no
     * holds answers zero in its own units rather than in none.
     */
    public Money activeTotalFor(AccountId account, Currency currency) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<AuthorizationId, Hold> entry : holds.entrySet()) {
            Hold hold = entry.getValue();
            if (hold.accountId().equals(account) && states.get(entry.getKey()) == AuthState.ACTIVE) {
                total = total.add(hold.amount().amount());
            }
        }
        return new Money(total.setScale(currency.getDefaultFractionDigits()), currency);
    }

    /** Authorizations against one account, in the order they were approved. */
    public List<AuthorizationId> idsFor(AccountId account) {
        List<AuthorizationId> result = new ArrayList<>();
        for (Map.Entry<AuthorizationId, Hold> entry : holds.entrySet()) {
            if (entry.getValue().accountId().equals(account)) {
                result.add(entry.getKey());
            }
        }
        return List.copyOf(result);
    }
}
