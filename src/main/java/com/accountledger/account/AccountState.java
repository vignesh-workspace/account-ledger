package com.accountledger.account;

/**
 * Whether an account is still accepting entries.
 *
 * <p>There is no DELETED state and there never will be. An account that has been closed
 * keeps every entry it ever had, because the balances of the days it was open are still true
 * statements about those days. Deleting it would make history depend on the present, which is
 * the same defect that makes "all balances return to their pre-debit values" impossible after
 * a reversal.
 */
public enum AccountState {
    OPEN,
    CLOSED
}
