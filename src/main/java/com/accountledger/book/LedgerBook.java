package com.accountledger.book;

import com.accountledger.account.Account;
import com.accountledger.account.AccountId;
import com.accountledger.account.AccountRegistry;
import com.accountledger.event.EventId;
import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The append-only journal of entries, and the only thing that knows a balance.
 *
 * <p>Entries go in and never come out. There is no update, no delete and no method that takes
 * a sequence number and changes anything. A correction is another entry pointing back at the
 * one it corrects, so the history of a mistake stays readable after the mistake is fixed.
 *
 * <p>The book is a projection in the sense that it could be discarded and rebuilt by replaying
 * the journal of events, which is what makes append-only structurally true rather than a
 * promise someone has to keep.
 */
public final class LedgerBook {

    private final AccountRegistry accounts;
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final Set<Long> reversed = new LinkedHashSet<>();
    private long nextSequence = 1;

    public LedgerBook(AccountRegistry accounts) {
        this.accounts = accounts;
    }

    /**
     * Appends an entry and returns it with its sequence number assigned. The caller cannot
     * choose the sequence: it is the book's record of arrival order, not an input.
     */
    public LedgerEntry append(
            EventId sourceEventId,
            AccountId accountId,
            EntryType type,
            Money amount,
            BusinessDay valueDay,
            BusinessDay bookingDay,
            Long reversesSequence) {
        Account account = accounts.require(accountId);
        if (!account.currency().equals(amount.currency())) {
            // Not a rejection. An AED instruction reaching a BHD account means something
            // upstream paired the wrong two things, and continuing would book a number whose
            // units are wrong.
            throw new com.accountledger.money.CurrencyMismatchException(
                    account.currency(), amount.currency());
        }
        if (reversesSequence != null && !reversed.add(reversesSequence)) {
            throw new IllegalStateException(
                    "Entry #" + reversesSequence + " has already been reversed. "
                            + "Ingest validation should have rejected this event.");
        }
        LedgerEntry entry = new LedgerEntry(nextSequence++, sourceEventId, accountId, type,
                amount, valueDay, bookingDay, reversesSequence);
        entries.add(entry);
        return entry;
    }

    /**
     * The balance of an account on a value day, as it was known on a knowledge day.
     *
     * <p>There is no single-argument convenience overload, and that is the design. "The
     * balance on day two" is not a question with an answer: evaluated on day two it is 250,
     * evaluated on day five, after a backdated debit arrives, it is −370. Making the
     * knowledge day mandatory means a caller cannot ask the ambiguous question by accident,
     * and an assertion about a restated balance documents itself at the call site.
     */
    public Money balanceAsOf(AccountId account, BusinessDay valueDay, BusinessDay knowledgeDay) {
        Account known = accounts.require(account);
        BigDecimal total = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.accountId().equals(account)
                    && entry.valueDay().isOnOrBefore(valueDay)
                    && entry.bookingDay().isOnOrBefore(knowledgeDay)) {
                total = total.add(entry.signedAmount());
            }
        }
        return new Money(total.setScale(known.currency().getDefaultFractionDigits()),
                known.currency());
    }

    /** The entry produced by a given event, if that event produced one. */
    public Optional<LedgerEntry> findBySourceEvent(EventId sourceEventId) {
        for (LedgerEntry entry : entries) {
            if (entry.sourceEventId().equals(sourceEventId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public boolean isReversed(long sequence) {
        return reversed.contains(sequence);
    }

    /** Every entry, in the order they were appended. */
    public List<LedgerEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<LedgerEntry> entriesFor(AccountId account) {
        List<LedgerEntry> result = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            if (entry.accountId().equals(account)) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return entries.size();
    }
}
