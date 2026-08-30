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

---

## 2026-08-30 16:28 UTC — event model, stream builder, remainder allocation

Renamed the project to `account-ledger` and moved the package root to
`com.accountledger`. Done now rather than later because a package rename costs one commit
today and touches every file in a week.

Events are modelled as a sealed interface over five records. Sealing is the point: dispatch
becomes an exhaustive `switch` and a sixth event kind is a compile error at every handler
rather than an unhandled case discovered at runtime. This is what replaces a visitor here,
without the double dispatch.

Two decisions came out of writing the records rather than planning them.

**A reversal has no amount.** The first draft gave every event an amount field. That is wrong
for a reversal: the amount belongs to whatever entry is being reversed and is not known until
that entry is found. Forcing a value would mean either duplicating it at submission, where it
could disagree with the original, or inventing a zero that means nothing. `amount()` now
returns null for reversals and the interface documents why. A reversal does carry its own
value day, and it is the original's, because a correction has to restate the same day the
error affected.

**Amounts are always positive; direction is carried by the event kind.** A debit of minus
five is a contradiction that a signed amount would let through, and the sign convention would
then have to be remembered at every call site. The constructors refuse it.

Built `RemainderAllocator` earlier than intended because the instalment credit in the
scenario cannot be expressed without it. Ten in a three-decimal currency does not divide by
three: 3.333 three times is 9.999 and 3.334 three times is 10.002, and neither is the amount
instructed. It works in minor units, where division is exact integer arithmetic, then hands
the leftover units out one each. Parts sum to the total by construction. The same allocator
will carry the interest rounding remainder later — they are the same problem, so they get one
implementation and one test suite.

Instalment expansion happens in the stream builder, at submission, not in the engine. The
instalments are what was actually instructed; the engine sees ordinary credits and needs no
concept of an instalment plan.

The stream builder never sorts. Order within a day decides outcomes, so a builder that
quietly reordered would change answers while looking like tidying. The scenario as given is
not in booking-day order — a reversal booking on day six is listed before a credit booking on
day five — which means the engine will need a stated rule for an instruction arriving after a
later day has closed. Not resolved yet; the test only pins that the order survives the
builder.

Scenario data lives in one test fixture and nowhere else.
`grep -rn "ACC-00\|Auth-[ABZ]" src/main/java` returns nothing, and that check now runs
before every commit. If it ever prints, the engine has stopped being general.

**State:** 31 assertions, all green.
