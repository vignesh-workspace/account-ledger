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

---

## 2026-08-30 17:06 UTC — the book, and what a balance is a function of

`balanceAsOf` takes a value day **and** a knowledge day, and there is no single-argument
overload. That was a deliberate refusal: the convenience method is the whole bug. "The day two
balance" is 250.00 asked on day two and −370.00 asked on day five, and a signature that lets a
caller ask without saying when makes the ambiguity invisible at every call site. With both days
mandatory, the criterion about the restated day two is written as
`balanceAsOf(account, day(2), day(5))` and needs no prose to explain it.

**There is no REVERSAL entry type.** The first sketch had one, and it does not work: the
direction of a reversal depends on what it reverses, so `signedAmount()` could not be answered
from the type alone without walking back to the original. Dropped it. Reversing a debit books a
**credit** — the money really does come back — and the fact that this credit is a correction is
recorded as `reversesSequence`, a link pointing backwards. Direction stays a property of the
type, amounts stay positive, and "has this been reversed" becomes a forward question the book
answers rather than a flag someone has to remember to set on the original.

Resolved a contradiction in the requirements while writing `AccountRegistry`. Unknown accounts
are described both as an ingest rejection and as something that throws. Both are right, at
different moments: an event naming an account that was never opened is bad **input**, and
crashing on it would discard every other event in the stream, so ingest rejects it and the day
report records it. After ingest, the same lookup throws, because an unknown account has stopped
being bad input and become a bug in the engine that let it past. `isKnown` is the question
ingest asks; `require` is the assertion everything downstream makes. Currency mismatch keeps
throwing in both places — an AED instruction against a BHD account is wrong units, and there is
no ledger to produce.

`LinkedHashMap` in the registry, not `HashMap`. The registry is iterated once per day to build
the report, and hash order varies with contents. A report whose rows moved between runs would
fail the determinism test for a reason that has nothing to do with the ledger.

Zero is allowed for an `OPENING` entry and refused for every other type. An account has to be
able to open empty; a zero credit moves nothing and would still print as a movement.

**State:** 47 assertions, all green.

---

## 2026-08-30 17:19 UTC — outcomes as values, holds as a separate projection

A refusal is a returned `Rejected`, never a thrown exception. Two reasons, and the second is
the one that decided it: an exception would unwind the replay and lose every event after the
bad one, and it would make "the ledger declined this authorization" — an ordinary, correct
business answer — indistinguishable from a defect in the engine. Every event produces exactly
one outcome and the day report prints all of them, so a rejection is a record rather than a
silence.

`RejectionReason` is an enum carrying its own sentence. The wording lives next to the constant
so the same refusal cannot be described three different ways in three different places, and a
test can assert on the reason without matching prose. Formatting is pinned to `Locale.ROOT`
after noticing the determinism claim reaches further than expected: `String.format` with a
default locale would produce a different report on a Turkish machine, and the determinism test
compares reports for equality. There is now a test that sets the default locale and checks the
output does not move.

**The hold registry is deliberately not bitemporal, and the ledger book is.** That asymmetry
looked wrong until the question was written out. A balance is asked about as of a past
knowledge day constantly — that is the whole backdating problem. An authorization's state is
only ever asked at the moment an instruction is being judged: is there still a hold against
this account right now. Nothing asks what an authorization's state was believed to be on day
three. Giving the registry knowledge-day queries would be machinery with no caller, so it
keeps the shape of the question actually asked. It stays a projection either way: discard it,
replay the journal, and it rebuilds identically.

`AuthState.EXPIRED` is defined and unreachable in this window, since no expiry rule was given.
Kept it anyway. Omitting it would imply an authorization can stay live forever, which is the
one answer that is certainly wrong, and the trade-off document has to enumerate every way an
authorization can end other than settling.

**State:** 59 assertions, all green.
