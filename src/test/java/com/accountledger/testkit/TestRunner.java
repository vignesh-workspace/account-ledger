package com.accountledger.testkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every {@link Test} method on the supplied classes and prints a report.
 *
 * <p>Methods are sorted by name so the run order is stable across JVMs; reflection order is
 * not guaranteed, and this suite makes determinism claims it has to honour itself.
 */
public final class TestRunner {

    private final List<String> failures = new ArrayList<>();
    private final List<String> expectedFailures = new ArrayList<>();
    private int passed;

    public void run(Class<?>... suites) {
        for (Class<?> suite : suites) {
            System.out.println("\n" + suite.getSimpleName());
            Method[] methods = suite.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method m : methods) {
                Test test = m.getAnnotation(Test.class);
                if (test == null) {
                    continue;
                }
                String label = test.value().isEmpty() ? m.getName() : test.value();
                Disabled disabled = m.getAnnotation(Disabled.class);
                try {
                    m.setAccessible(true);
                    m.invoke(suite.getDeclaredConstructor().newInstance());
                    if (disabled != null) {
                        // A known-failing test that starts passing is itself news.
                        failures.add(suite.getSimpleName() + " > " + label
                                + "\n      marked @Disabled but PASSED; the design changed, update the annotation");
                        System.out.println("  [!!] " + label + "  (expected to fail, but passed)");
                    } else {
                        passed++;
                        System.out.println("  [ok] " + label);
                    }
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (disabled != null) {
                        expectedFailures.add(suite.getSimpleName() + " > " + label
                                + "\n      " + disabled.value()
                                + "\n      failed with: " + oneLine(cause));
                        System.out.println("  [xf] " + label + "  (known gap, see report)");
                    } else {
                        failures.add(suite.getSimpleName() + " > " + label + "\n      "
                                + oneLine(cause));
                        System.out.println("  [FAIL] " + label);
                    }
                } catch (ReflectiveOperationException e) {
                    failures.add(suite.getSimpleName() + " > " + label
                            + "\n      could not invoke: " + e);
                    System.out.println("  [FAIL] " + label + " (invocation error)");
                }
            }
        }
        report();
    }

    private static String oneLine(Throwable t) {
        String msg = t.getMessage() == null ? t.toString() : t.getMessage();
        return msg.replace("\n", "\n      ");
    }

    private void report() {
        System.out.println("\n" + "=".repeat(72));
        System.out.printf("passed: %d   failed: %d   known gaps: %d%n",
                passed, failures.size(), expectedFailures.size());
        if (!expectedFailures.isEmpty()) {
            System.out.println("\nKnown gaps (expected failures, documented in REJECTED.md):");
            for (String xf : expectedFailures) {
                System.out.println("  - " + xf);
            }
        }
        if (!failures.isEmpty()) {
            System.out.println("\nFailures:");
            for (String f : failures) {
                System.out.println("  - " + f);
            }
            System.out.println("=".repeat(72));
            System.exit(1);
        }
        System.out.println("=".repeat(72));
    }
}
