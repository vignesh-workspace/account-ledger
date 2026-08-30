package com.accountledger.event;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.money.RemainderAllocator;
import com.accountledger.time.BusinessDay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds an ordered list of events.
 *
 * <p>Submission order is preserved exactly as written and is never sorted here. Order within
 * a day changes outcomes — an authorization judged before a large debit and the same
 * authorization judged after it are different questions with different answers — so
 * reordering a stream silently would change results while looking like a tidy-up.
 *
 * <p>A typed builder rather than a file format. A parser for a text or CSV stream would buy
 * scenario authoring that is not needed twice here, and cost a grammar, its error handling
 * and its own tests. This keeps the compiler as the validator: a malformed event will not
 * build.
 */
public final class EventStream {

    private EventStream() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<LedgerEvent> events = new ArrayList<>();
        private final Set<EventId> ids = new LinkedHashSet<>();

        public Builder credit(
                String eventId, int bookingDay, AccountId account, Money amount, int valueDay) {
            return add(new Credit(EventId.of(eventId), BusinessDay.of(bookingDay), account,
                    amount, BusinessDay.of(valueDay)));
        }

        public Builder debit(
                String eventId, int bookingDay, AccountId account, Money amount, int valueDay) {
            return add(new Debit(EventId.of(eventId), BusinessDay.of(bookingDay), account,
                    amount, BusinessDay.of(valueDay)));
        }

        public Builder authorization(String eventId, int bookingDay, AccountId account,
                String authorizationId, Money amount, int valueDay) {
            return add(new Authorization(EventId.of(eventId), BusinessDay.of(bookingDay), account,
                    AuthorizationId.of(authorizationId), amount, BusinessDay.of(valueDay)));
        }

        public Builder settlement(String eventId, int bookingDay, AccountId account,
                String authorizationId, Money amount, int valueDay) {
            return add(new Settlement(EventId.of(eventId), BusinessDay.of(bookingDay), account,
                    AuthorizationId.of(authorizationId), amount, BusinessDay.of(valueDay)));
        }

        public Builder closeAccount(String eventId, int bookingDay, AccountId account) {
            return add(new AccountClosure(EventId.of(eventId), BusinessDay.of(bookingDay), account));
        }

        public Builder reversal(String eventId, int bookingDay, AccountId account,
                String reversedEventId, int valueDay) {
            return add(new Reversal(EventId.of(eventId), BusinessDay.of(bookingDay), account,
                    EventId.of(reversedEventId), BusinessDay.of(valueDay)));
        }

        /**
         * Expands one credit into equal instalments that sum to exactly the total.
         *
         * <p>Expansion happens here, at submission, rather than inside the engine. The
         * instalments are what was actually instructed; the engine sees them as ordinary
         * credits and needs no concept of an instalment plan. Ids are derived from the parent
         * so the grouping stays visible in the journal.
         */
        public Builder creditInInstalments(String eventId, int bookingDay, AccountId account,
                Money total, int instalments, int valueDay) {
            EventId parent = EventId.of(eventId);
            List<Money> parts = RemainderAllocator.split(total, instalments);
            for (int i = 0; i < parts.size(); i++) {
                add(new Credit(parent.part(i + 1), BusinessDay.of(bookingDay), account,
                        parts.get(i), BusinessDay.of(valueDay)));
            }
            return this;
        }

        private Builder add(LedgerEvent event) {
            if (!ids.add(event.eventId())) {
                throw new IllegalArgumentException(
                        "Duplicate event id in stream: " + event.eventId());
            }
            events.add(event);
            return this;
        }

        public List<LedgerEvent> build() {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
    }
}
