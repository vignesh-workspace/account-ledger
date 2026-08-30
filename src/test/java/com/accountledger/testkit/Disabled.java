package com.accountledger.testkit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that is expected to fail against the current design. The runner reports it
 * separately and does not fail the build, but it is never silently skipped: the reason is
 * printed every run so the known gap stays visible.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Disabled {
    String value();
}
