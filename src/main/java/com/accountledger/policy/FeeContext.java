package com.accountledger.policy;

import com.accountledger.account.Account;
import com.accountledger.book.LedgerBook;
import com.accountledger.time.BusinessDay;

/**
 * Everything a fee policy is allowed to look at.
 *
 * <p>The book is passed rather than a precomputed balance, deliberately. A forward-only policy
 * needs one number and a restatement policy needs to re-derive every earlier day as of today;
 * handing over a single closing balance would make the second policy impossible to write and
 * so would quietly settle the question the policy exists to ask.
 *
 * <p>The book is given as the interface it already exposes, which has no mutator a policy
 * could reach. A policy decides; the day-close books.
 */
public record FeeContext(
        Account account,
        BusinessDay day,
        BusinessDay windowStart,
        BusinessDay windowEnd,
        LedgerBook book) {
}
