package com.accountledger.policy;

import java.util.List;

/**
 * Decides what overdraft fees, if any, a day close should book.
 *
 * <p>Returns a list rather than an {@code Optional} so that a policy which restates history
 * can express what it actually concludes — several fees at once — instead of being forced
 * through a shape that assumes the answer is at most one. The list is the assessment; nothing
 * here books anything.
 */
public interface OverdraftFeePolicy {

    List<FeeAssessment> assess(FeeContext context);
}
