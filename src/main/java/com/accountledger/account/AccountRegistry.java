package com.accountledger.account;

import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The set of accounts known to a replay, and when each of them opened or closed.
 *
 * <p>Backed by a {@link LinkedHashMap} rather than a {@link java.util.HashMap}: the registry
 * is iterated to build every day report, and hash order varies with the contents and the JVM.
 * A report whose account rows change position between runs would break the determinism test
 * for a reason that has nothing to do with the ledger.
 *
 * <p>Two different failure modes are handled two different ways, and the difference is the
 * point:
 * <ul>
 *   <li>Opening the same account twice <em>throws</em>. Nothing in a submitted stream can
 *       cause it; it means the configuration was written wrong, and there is no sensible
 *       ledger to produce.</li>
 *   <li>An event naming an account that was never opened is <em>rejected</em> at ingest and
 *       recorded in the day report. Bad input is an expected condition for something reading
 *       an external stream; crashing on it would lose every other event in the file.</li>
 * </ul>
 * Once ingest has passed, {@link #require} throws, because by then an unknown account is no
 * longer bad input but a bug in the engine that let it through.
 */
public final class AccountRegistry {

    private final Map<AccountId, Account> accounts = new LinkedHashMap<>();
    private final Map<AccountId, BusinessDay> closedOn = new LinkedHashMap<>();

    /** Registers an account. Throws if it is already known. */
    public Account open(AccountId id, Currency currency, BusinessDay openedOn) {
        Objects.requireNonNull(id, "id");
        if (accounts.containsKey(id)) {
            throw new IllegalStateException("Account already opened: " + id);
        }
        Account account = new Account(id, currency, openedOn);
        accounts.put(id, account);
        return account;
    }

    /** Records a closure. Throws if the account is unknown or already closed. */
    public void close(AccountId id, BusinessDay day) {
        require(id);
        if (closedOn.containsKey(id)) {
            throw new IllegalStateException("Account already closed: " + id);
        }
        closedOn.put(id, day);
    }

    public boolean isKnown(AccountId id) {
        return accounts.containsKey(id);
    }

    public Optional<Account> find(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    /** Looks up an account that ingest has already validated. Throws if it is missing. */
    public Account require(AccountId id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw new IllegalStateException(
                    "Unknown account reached the engine: " + id
                            + ". Ingest validation should have rejected this event.");
        }
        return account;
    }

    /**
     * The state of an account as it was known on a given day. A closure that has not yet
     * happened on the day being asked about does not make the account closed then: the
     * question "was this account open on day three" has a fixed answer that a later closure
     * cannot change.
     */
    public AccountState stateOn(AccountId id, BusinessDay knowledgeDay) {
        BusinessDay closure = closedOn.get(id);
        return closure != null && closure.isOnOrBefore(knowledgeDay)
                ? AccountState.CLOSED
                : AccountState.OPEN;
    }

    public Optional<BusinessDay> closureDay(AccountId id) {
        return Optional.ofNullable(closedOn.get(id));
    }

    /** Accounts in registration order. */
    public List<Account> all() {
        return List.copyOf(new ArrayList<>(accounts.values()));
    }

    public int size() {
        return accounts.size();
    }
}
