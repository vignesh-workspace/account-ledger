package com.accountledger.report;

import com.accountledger.account.AccountId;
import com.accountledger.account.AccountState;
import com.accountledger.money.Money;

/**
 * One account's position at the close of one day.
 *
 * <p>Both closing balances are carried. {@code beforeFees} is what the fee rule was tested
 * against; {@code closing} is what stands once any fee is booked, and is what interest accrues
 * on. On a day with no fee they are the same number, and on the day the overdraft fee lands
 * they are -155.00 and -180.00. Reporting only one of them would leave the reader unable to
 * check the fee decision against the balance that produced it.
 *
 * <p>A record, not a class with getters, because the determinism test compares two whole
 * replays for equality and needs that comparison to be structural.
 */
public record AccountDayReport(
        AccountId account,
        AccountState state,
        Money closingBeforeFees,
        Money closing,
        Money holds,
        Money available,
        Money feesCharged,
        Money interestPublished) {
}
