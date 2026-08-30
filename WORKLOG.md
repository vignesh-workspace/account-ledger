# Worklog

Timestamps are UTC, taken from the machine at the time of writing. Entries record decisions
and reversals, not a narrative of progress. `git log --format='%h %ad %s' --date=iso` is the
independent record.

---

## 2026-08-30 16:14 UTC — foundations: money, days, test harness

Probed the environment before writing anything. Three findings changed the plan.

**Maven Central is unreachable.** `mvn` is not installed, and the egress proxy returns
`x-deny-reason: host_not_allowed` for both `repo.maven.apache.org` and `repo1.maven.org`.
JUnit cannot be fetched. Chose to build with **zero external dependencies** and write a small
test harness rather than work around the network. This is the better answer for something
another person has to run: no download step, no dependency resolution, no version drift.
The cost — no parameterised tests, no automatic discovery — is recorded in REJECTED.md.

**The image shipped a JRE layout.** `java` was present but `javac` was not, though the
in-process compiler API was reachable via `ToolProvider`. Rejected building on that: a normal
machine has `javac`, and a bootstrap compiler driven through the compiler API would be a
clever solution to a problem nobody else has. Installed `openjdk-21-jdk-headless` from the
Ubuntu archive instead so `run.sh` stays conventional.

**Java 21 rather than 17.** Taking it deliberately. Exhaustive pattern-matching `switch` over
sealed interfaces is final in 21 and is what replaces the Visitor pattern for event dispatch;
on 17 it needs `--enable-preview`.

Verified fraction digits against the JDK: AED 2, BHD 3, KWD 3, JPY 0. Currency precision is
therefore **derived from `java.util.Currency` and never declared as a constant**. A JPY
account would round fees and interest to whole yen with no code change.

`Money` ended up with two construction paths, which was not the intent going in. Writing the
tests surfaced the conflict: a single factory cannot both refuse `Money.of("1.005", AED)` as
a typo and accept a computed interest accrual that legitimately needs rounding. Split into
`of` for literals, which throws when the literal is finer than the currency, and `round` for
computed values, which rounds HALF_UP because the caller asked for it. That distinction is
load-bearing later: it is what allows the interest accrual to round exactly once, at the
point it is published.

`BusinessDay` is an ordinal rather than a `LocalDate`. Nothing in the requirements anchors a
day to a calendar, so a date type would force inventing a timezone, a weekend rule and a
holiday calendar that no rule asks for. Logged as an ambiguity.

The runner treats a known-failing test that starts passing as a build failure, not a quiet
success. A gap that closes without anyone noticing is news that gets lost.

Left alone for now: events, balances, holds. This is only the value types everything else
rests on.

**State:** 13 assertions, all green, via `./run.sh test`.
