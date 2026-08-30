package com.accountledger;

import com.accountledger.account.AccountRegistryTest;
import com.accountledger.book.LedgerBookTest;
import com.accountledger.engine.DeterminismTest;
import com.accountledger.engine.EngineModesTest;
import com.accountledger.engine.ScenarioDecisionsTest;
import com.accountledger.engine.ScenarioReplayTest;
import com.accountledger.event.EventStreamTest;
import com.accountledger.hold.HoldRegistryTest;
import com.accountledger.outcome.OutcomeTest;
import com.accountledger.policy.AuthorizationDecisionPolicyTest;
import com.accountledger.policy.FlatOverdraftFeePolicyTest;
import com.accountledger.policy.InterestAccrualPolicyTest;
import com.accountledger.money.MoneyTest;
import com.accountledger.money.RemainderAllocatorTest;
import com.accountledger.testkit.TestRunner;
import com.accountledger.time.BusinessDayTest;

/**
 * Entry point for the suite. Test classes are listed explicitly rather than discovered by
 * scanning the classpath: the list is short, and an explicit list cannot silently drop a
 * suite because a naming convention drifted.
 */
public final class AllTests {
    public static void main(String[] args) {
        new TestRunner().run(
                MoneyTest.class,
                RemainderAllocatorTest.class,
                EventStreamTest.class,
                AccountRegistryTest.class,
                LedgerBookTest.class,
                HoldRegistryTest.class,
                OutcomeTest.class,
                FlatOverdraftFeePolicyTest.class,
                InterestAccrualPolicyTest.class,
                AuthorizationDecisionPolicyTest.class,
                ScenarioReplayTest.class,
                ScenarioDecisionsTest.class,
                EngineModesTest.class,
                DeterminismTest.class,
                BusinessDayTest.class);
    }
}
