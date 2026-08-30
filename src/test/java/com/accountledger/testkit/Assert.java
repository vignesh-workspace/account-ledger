package com.accountledger.testkit;

import java.util.Objects;

/** Minimal assertions. No dependency is reachable from this build environment, so this is it. */
public final class Assert {

    private Assert() {}

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + "\n      expected: " + expected + "\n      actual:   " + actual);
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    /** Asserts the body throws the given type, and returns the exception for further checks. */
    public static <T extends Throwable> T assertThrows(
            Class<T> expected, Runnable body, String message) {
        try {
            body.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return expected.cast(actual);
            }
            throw new AssertionError(
                    message + "\n      expected: " + expected.getSimpleName()
                            + "\n      actual:   " + actual.getClass().getSimpleName()
                            + ": " + actual.getMessage());
        }
        throw new AssertionError(
                message + "\n      expected " + expected.getSimpleName() + " but nothing was thrown");
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
