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

---

## 2026-08-30 17:41 UTC — the three policies, and the interest contradiction resolved

The rules require each day to publish a rounded accrual **and** the daily accruals to sum
exactly to what is capitalised. Over this scenario those two requirements give 0.83 and 0.82.
That is not a hypothetical tension, so there is now a test that asserts both numbers and the
disagreement between them, rather than a paragraph claiming it.

Chose the **running remainder carry**: each day publishes
`round(cumulative unrounded) - everything published so far`. The running total is therefore
always the correctly rounded total and the remainder moves into the next day instead of being
dropped or double-counted. Summing exactly becomes a property of the construction. It is the
same largest-remainder discipline as the instalment split, one day later, so one idea serves
both places rather than two mechanisms for one problem.

The cost is visible and the report has to footnote it: day six publishes 0.17 where
`440.00 x 0.0004` rounded on its own is 0.18. Asserted directly, both numbers in one test, so
nobody later "fixes" it.

Built the accrue-then-sum alternative in **test scope** to measure the drift instead of
asserting it. Over a thousand days at a balance accruing 0.0051 a day — just above half a minor
unit, so it rounds up every single day — it publishes 10.00 against the 5.10 actually owed. Put
it in test scope deliberately: an implementation sitting in the engine behind a configuration
flag would eventually be selected by someone who found the flag before the argument.

`InterestAccrualPolicy` is a **stateful** strategy, which is unusual and is the honest shape.
The figure published for a day is not a function of that day's balance alone; it depends on
what has already been published, and no stateless signature can satisfy the sum-exactly rule.
An instance belongs to one replay and the javadoc says so.

The fee policy returns a **list**, not an `Optional`. A restatement policy concludes several
fees at once, and forcing it through a shape that assumes at most one would quietly settle the
question the policy exists to ask. The value day is an output of the policy too, since the two
readings differ on exactly that point.

Fees are configured **per currency** with no conversion. A single amount applied to every
currency would assert that twenty-five dirhams and twenty-five dinars are the same charge,
which is out by roughly a factor of ten. A fee falling due in an unconfigured currency throws:
inventing a number and silently skipping the fee are both worse than stopping.

**State:** 77 assertions, all green.

---

## 2026-08-30 18:04 UTC — the engine, and a bug the determinism rule caught before it existed

The replay reproduces every target figure on the first run: 250.00, 250.00 with 200.00 held,
650.00, 465.00, −155.00 before the fee and −180.00 after it, 440.00, capitalising 0.82 to
finish at 440.82; and 10.000 on the three-decimal account finishing at 10.008.

`LedgerConfig` takes the interest policy as a **factory**, not an instance. Writing it the
obvious way, two engines built from one config would share one policy, and the second replay
would continue the first replay's remainder carry and publish different figures for identical
input. The determinism requirement is what surfaced it — the test builds two engines from one
config precisely to catch that shape of mistake — and handing over a factory makes the mistake
unavailable rather than merely detected.

**Added a sixth event kind, `AccountClosure`.** The architecture note lists five records, but
closing has to land in the journal and replay with everything else, and a closure applied
through a registry method would be invisible to a rebuild from the journal. Opening stays a
configuration operation that throws on a duplicate, and closing is an event that produces a
rejection: asymmetric, and exactly what the two are. A duplicate open is a configuration
written wrong; a second closure is an instruction that arrived twice.

Resolved the fee-currency question by refusing to answer it. The fee is given as AED 25.00 and
the second account is in BHD. A single amount applied to both would assert that twenty-five
dirhams and twenty-five dinars are the same charge. Fees are configured per currency and a fee
falling due in an unconfigured currency throws. It never fires in this scenario, because that
account never goes negative — which is the point: nothing was invented to cover a case the
rules do not describe.

Two decisions taken while writing the replayer, both recorded as ambiguities:

- **A partial settlement closes the authorization and releases the remainder** rather than
  leaving the difference held. The merchant has said what the transaction was worth; continuing
  to reserve the rest restricts funds against a completed instruction. The other reading is
  defensible and the day report shows the release, so a reader can see which was chosen.
- **Interest accrued while an account was open is still capitalised at the end of the window,
  even if the account closed in between.** The money was earned and is owed. It does mean the
  ledger books an entry to a closed account, which sits awkwardly beside the rule that a closed
  account takes no further entries — that rule governs instructions, not the ledger's own
  obligations. Asserted in the closure test so the choice is visible rather than incidental.

The criterion described as vacuous — a hold reducing available balance without moving the
ledger balance — turns out **not** to be vacuous. The first authorization is approved on day
two, and that day shows a ledger balance of 250.00 unchanged with available at 50.00. It is
asserted directly against the scenario rather than against a synthetic one.

`DayCloseProcessor.close` is `final` and its stages are not. Assessing the fee before the day's
instructions are processed would test a balance that has not happened; accruing interest before
the fee is booked would pay interest on money already taken. Both are silent errors, and the
order being unavailable for rearrangement is what stops them.

**State:** 97 assertions, all green.

---

## 2026-08-30 18:31 UTC — the harness, and why the report types are records

`./run.sh report` now prints the window: instructions and their outcomes, positions, live
authorization states, then the end-of-window capitalisation and a conservation check. It exits
non-zero if any account fails to reconcile, so it is a check and not only something to read.

The **conservation check** is ten lines and is the only structural guard here against a bug that
creates money. Entries are single-sided — a credit of 400.00 has no matching debit anywhere — so
nothing in this ledger nets to zero and double entry cannot be used to prove it consistent. What
can be proved is narrower and still worth having: each account's entries must sum to its final
balance. It runs in the harness and again as an assertion in the suite.

Report types are records, and that is what makes the determinism test mean anything. Comparing
two replays for equality has to be structural. If a day report were a class with a formatted
`toString`, "the same report" would collapse into "the same text", and two runs could agree on
every printed character while disagreeing about a number the text does not happen to print.

The determinism test runs the stream through two engines built from **one** config as well as
two, which is the case that would have caught the shared-interest-policy mistake. Its assertion
names the number it is protecting: the second replay capitalises 0.82, not 1.64.

`LedgerHarness` lives in test scope alongside the scenario, not in the engine. It has to name
accounts and authorizations to run the scenario at all, and the rule that scenario identifiers
never appear in `src/main/java` is worth more than the convention that a main method lives in
the main tree. `ReportPrinter` is in the engine, because it names nothing.

Also noticed while reading the printed report: an authorization that has finished stays listed
on every later day rather than disappearing. Keeping it was deliberate — a settled authorization
that vanished would make the day it settled look like the day it never existed.

**State:** 101 assertions, all green.

---

## 2026-08-30 18:52 UTC — refuting the criteria by running them, and one test left failing

Built `RestatementFeePolicy` in test scope and pointed the engine at it. Against the scenario it
charges **three** fees, value-dated on days two, four and five — the days whose views re-derive
negative once the backdated debit is known. Forward-only charges one, on day five. So the
criterion asking for exactly one fee assessed on day two is not reachable under either reading,
and that is now a number a test produces rather than an argument in a document.

Writing that policy exposed a real defect in the engine. Fee entries took their source id from
the account and the day being closed, which is unique only while a close books at most one fee.
The restating policy books three in a single close, and all three would have shared an id, so a
later reversal naming that id could not have said which fee it meant. The value day is now part
of the id. A one-fee-per-day assumption had been baked into an identifier without anyone
deciding it, and only a second policy could have found it.

Also: `RestatementFeePolicy` first looked its fee up before deciding whether any fee was due,
which made it throw on the three-decimal account that never overdraws. Moved the lookup to the
point a fee is actually owed, matching the shipped policy. Worth recording because it is the
same mistake in both directions — the configuration gap should only be fatal when it actually
blocks an answer.

**The failing test.** One, `@Disabled`, with its reason in the annotation and the reasoning
inline: a backdated debit should charge the fee on the day the balance actually went negative,
not the day the instruction arrived. It fails, expecting Day 2 and getting Day 5, and the runner
prints exactly that every run. Closing the gap is not a different line of code — it needs a
back-valuation run that reopens closed days and an operational approval gate in front of it,
because that run moves money on days a customer has already been told about. Both are named in
the architecture document as cut.

The runner fails the build if a disabled test starts passing, so the gap cannot close quietly.
The reasoning lives in the test rather than in prose for the same reason: a limitation in a
document is checked when somebody happens to read it, and stops being true without anyone
noticing.

**State:** 105 assertions green, 1 known gap.

---

## 2026-08-30 19:14 UTC — the documents, and the one deliverable that needed a workaround

Wrote the four documents plus the architecture and trade-off paper. They describe the system and
say nothing about the order it was built in; this worklog is the only file that records that, and
it is the right place for it.

`AMBIGUITIES.md` came out at eighteen entries rather than the fourteen anticipated. The four
extra were all found by writing code rather than by reading requirements: the unknown-account
contradiction, interest on an account that closes mid-window, which value day a reversal takes,
and a settlement larger than its hold. Every entry names a test, and where an entry did not have
one it was either given one or the resolution was not real.

**No converter was available for the PDF.** No pandoc, no LibreOffice, no working Python on this
machine. Rejected writing a PDF generator in Java: PDF is a text format and a text-only one is
perhaps a hundred lines, but it would be a second unrelated piece of software living in a ledger
repository, and it is the same mistake as the bootstrap compiler that was rejected on the first
day.

Used what the machine already has instead. A twenty-line awk script converts the markdown subset
actually used — headings, tables, bold, code spans, rules — into print-styled HTML, and headless
Edge prints it to PDF in one command. The script lives outside the repository because it is a
tool for producing a deliverable, not part of the ledger. `ARCHITECTURE.pdf` comes out at four
pages, inside the stated range.

The markdown is committed alongside the PDF deliberately: the PDF is what was asked for, and the
markdown is what can be diffed when a paragraph changes.

**State:** 105 assertions green, 1 known gap. Nine commits.
