package com.accountledger.policy;

import com.accountledger.account.Account;
import com.accountledger.money.Money;

/**
 * Decides how much interest an account publishes on the day being closed.
 *
 * <p>An instance holds the running state of one replay and must not be shared between two.
 * That is unusual for a strategy and it is the honest shape here: the published figure for a
 * day is not a function of that day's balance alone. It depends on what has already been
 * published, because the daily figures are required to sum exactly to the capitalised total,
 * and no stateless signature can satisfy that requirement.
 */
public interface InterestAccrualPolicy {

    /** The accrual to publish for {@code account} at the close of a day. May be zero. */
    Money publish(Account account, Money closingBalance);

    /** Everything published for {@code account} so far, which is what capitalises. */
    Money publishedTotal(Account account);
}
