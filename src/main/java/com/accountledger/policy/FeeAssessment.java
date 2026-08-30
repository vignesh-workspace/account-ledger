package com.accountledger.policy;

import com.accountledger.money.Money;
import com.accountledger.time.BusinessDay;
import java.util.Objects;

/**
 * A fee a policy has decided to charge, and the day it belongs to.
 *
 * <p>The value day is carried separately from the day the assessment ran, because the two
 * readings of the fee rule differ on exactly that point: a forward-only policy always books
 * with value day equal to the day assessed, while a restatement policy books against the
 * historical day whose balance re-derives negative. Making the value day an output of the
 * policy is what lets both be expressed without the day-close knowing which it is talking to.
 */
public record FeeAssessment(Money amount, BusinessDay valueDay, String reason) {

    public FeeAssessment {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(valueDay, "valueDay");
        Objects.requireNonNull(reason, "reason");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A fee must be positive, got " + amount);
        }
    }
}
