package com.accountledger;

import com.accountledger.event.EventStreamTest;
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
                BusinessDayTest.class);
    }
}
