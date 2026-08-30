package com.accountledger.engine;

import com.accountledger.account.AccountId;
import com.accountledger.money.Money;
import com.accountledger.policy.AuthorizationDecisionPolicy;
import com.accountledger.policy.AvailableBalanceDecisionPolicy;
import com.accountledger.policy.FlatOverdraftFeePolicy;
import com.accountledger.policy.InterestAccrualPolicy;
import com.accountledger.policy.OverdraftFeePolicy;
import com.accountledger.policy.RunningCarryInterestPolicy;
import com.accountledger.time.BusinessDay;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything an engine needs that is not an instruction: the window, the accounts, the
 * policies and one behavioural switch.
 *
 * <p>The interest policy is supplied as a <strong>factory</strong> rather than an instance, and
 * that is not decoration. The chosen policy carries a running remainder per account, so two
 * engines sharing one instance would have the second replay continue the first replay's carry
 * and publish different figures for identical input. The determinism test builds two engines
 * from one config precisely to catch that class of mistake, and it would have caught this one
 * as a failure rather than as a design. Handing over a factory makes the mistake unavailable.
 *
 * <p>{@code strictArrivalOrder} chooses between the two readings of an instruction that arrives
 * after a later day has closed. It is a flag rather than a comment because both readings are
 * defensible and the one in force should be visible in the configuration and provable by a
 * test, not assumed.
 */
public final class LedgerConfig {

    /** An account to open before the replay begins, and what it starts with. */
    public record AccountOpening(
            AccountId id, Currency currency, BusinessDay openedOn, Money openingBalance) {
    }

    private final BusinessDay windowStart;
    private final BusinessDay windowEnd;
    private final List<AccountOpening> openings;
    private final OverdraftFeePolicy feePolicy;
    private final Supplier<InterestAccrualPolicy> interestPolicy;
    private final AuthorizationDecisionPolicy decisionPolicy;
    private final boolean strictArrivalOrder;

    private LedgerConfig(Builder builder) {
        this.windowStart = builder.windowStart;
        this.windowEnd = builder.windowEnd;
        this.openings = List.copyOf(builder.openings);
        this.decisionPolicy = builder.decisionPolicy;
        this.strictArrivalOrder = builder.strictArrivalOrder;
        this.feePolicy = builder.feePolicy != null
                ? builder.feePolicy
                : new FlatOverdraftFeePolicy(builder.fees);
        BigDecimal rate = builder.dailyInterestRate;
        this.interestPolicy = builder.interestPolicy != null
                ? builder.interestPolicy
                : () -> new RunningCarryInterestPolicy(rate);
    }

    public static Builder builder() {
        return new Builder();
    }

    public BusinessDay windowStart() {
        return windowStart;
    }

    public BusinessDay windowEnd() {
        return windowEnd;
    }

    /** Every day in the window, ascending. */
    public List<BusinessDay> window() {
        List<BusinessDay> days = new ArrayList<>();
        for (BusinessDay day = windowStart; day.isOnOrBefore(windowEnd); day = day.next()) {
            days.add(day);
        }
        return List.copyOf(days);
    }

    public boolean isInWindow(BusinessDay day) {
        return !day.isBefore(windowStart) && day.isOnOrBefore(windowEnd);
    }

    public List<AccountOpening> openings() {
        return openings;
    }

    public OverdraftFeePolicy feePolicy() {
        return feePolicy;
    }

    /** A fresh interest policy for one replay. Never reuse one across replays. */
    public InterestAccrualPolicy newInterestPolicy() {
        return interestPolicy.get();
    }

    public AuthorizationDecisionPolicy decisionPolicy() {
        return decisionPolicy;
    }

    public boolean strictArrivalOrder() {
        return strictArrivalOrder;
    }

    public static final class Builder {

        private BusinessDay windowStart = BusinessDay.of(1);
        private BusinessDay windowEnd = BusinessDay.of(1);
        private final List<AccountOpening> openings = new ArrayList<>();
        private final Map<Currency, Money> fees = new LinkedHashMap<>();
        private BigDecimal dailyInterestRate = BigDecimal.ZERO;
        private OverdraftFeePolicy feePolicy;
        private Supplier<InterestAccrualPolicy> interestPolicy;
        private AuthorizationDecisionPolicy decisionPolicy = new AvailableBalanceDecisionPolicy();
        private boolean strictArrivalOrder;

        public Builder window(int firstDay, int lastDay) {
            this.windowStart = BusinessDay.of(firstDay);
            this.windowEnd = BusinessDay.of(lastDay);
            if (windowEnd.isBefore(windowStart)) {
                throw new IllegalArgumentException(
                        "Window ends before it starts: " + windowStart + " to " + windowEnd);
            }
            return this;
        }

        public Builder account(AccountId id, Currency currency, int openedOn, Money opening) {
            openings.add(new AccountOpening(id, currency, BusinessDay.of(openedOn), opening));
            return this;
        }

        /** Registers the overdraft fee for the currency the amount is denominated in. */
        public Builder overdraftFee(Money fee) {
            fees.put(fee.currency(), fee);
            return this;
        }

        public Builder dailyInterestRate(BigDecimal rate) {
            this.dailyInterestRate = rate;
            return this;
        }

        public Builder feePolicy(OverdraftFeePolicy policy) {
            this.feePolicy = policy;
            return this;
        }

        public Builder interestPolicy(Supplier<InterestAccrualPolicy> factory) {
            this.interestPolicy = factory;
            return this;
        }

        public Builder decisionPolicy(AuthorizationDecisionPolicy policy) {
            this.decisionPolicy = policy;
            return this;
        }

        public Builder strictArrivalOrder(boolean strict) {
            this.strictArrivalOrder = strict;
            return this;
        }

        public LedgerConfig build() {
            return new LedgerConfig(this);
        }
    }
}
