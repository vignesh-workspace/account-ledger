package com.accountledger.report;

import com.accountledger.account.AccountId;
import com.accountledger.event.AuthorizationId;
import com.accountledger.hold.AuthState;
import com.accountledger.money.Money;

/**
 * Where one authorization stood at the close of a day.
 *
 * <p>Every authorization the replay has approved appears on every day from then on, including
 * the ones that have finished. A settled authorization that vanished from the report would
 * make the day it settled look like the day it never existed.
 */
public record AuthorizationStatus(
        AuthorizationId id, AccountId account, Money amount, AuthState state) {
}
