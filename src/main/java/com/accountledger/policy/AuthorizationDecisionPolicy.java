package com.accountledger.policy;

import com.accountledger.money.Money;

/**
 * Decides whether a proposed hold may be placed.
 *
 * <p>Isolated from the placing of the hold on purpose. The decision is a predicate over three
 * numbers and nothing else — no clock, no registry, no book — which makes the load-bearing
 * property of the scenario testable directly: the same authorization approved before a large
 * backdated debit and declined after it, with nothing changing but the balance passed in.
 */
public interface AuthorizationDecisionPolicy {

    boolean approves(Money ledgerBalance, Money activeHolds, Money proposedHold);
}
