# Rejected

Two sections. The first refuses four acceptance criteria that are arithmetically wrong. The
second records approaches abandoned during the build, and what each cost.

---

# Acceptance criteria that cannot be met

Each of these is refused with the arithmetic, and each is refuted by a test that runs rather
than by an argument.

## 1. "Exactly one fee, assessed on day two"

The count is right. The day is not, and no reading of the rule produces both.

The rule books the fee with value date equal to the day assessed. Day two closed with a
balance of 250.00 and nothing to charge. It only re-derives negative once a debit booked on
day five takes value from day two, and by then day two has closed.

**Forward-only assessment** — the shipped reading — charges one fee, on day five, whose own
closing balance of −155.00 is negative in its own right.

**Restatement**, re-deriving every earlier day against today's knowledge, charges **three**:

| Day | Balance re-derived on day five | Charged |
|---|---|---|
| 1 | 250.00 | no |
| 2 | 1200 − 950 − 620 = −370.00 | yes |
| 3 | 30.00 | no |
| 4 | −155.00 | yes |
| 5 | −155.00 | yes |

Three fees, value-dated days two, four and five, contradicting "exactly one". So one reading
gives one fee on day five and the other gives three fees across three days. Nothing gives one
fee on day two.

*Refuted by* `RejectedCriteriaTest.oneFeeOnDayTwoIsUnreachable`, which runs the scenario under
both policies and asserts both counts. `RestatementFeePolicy` exists in test scope for exactly
this purpose.

## 2. "After the reversal, all balances and fees return to their pre-debit values"

Impossible in an append-only ledger, and undesirable in any ledger.

Before the backdated debit, the account stood at 465.00. After reversing it, the account stands
at **440.00**, and the missing 25.00 is the overdraft fee. Three things do not come back:

- **The fee stands.** It was correctly assessed on day five against a balance that was genuinely
  negative on day five. The reversal does not make that assessment retrospectively wrong.
- **Day five's interest accrual is gone.** The balance was negative at that day's close, so
  nothing accrued, and no later event pays it retrospectively.
- **The declined authorization stays declined.** It was judged against the balance in front of it
  at the moment it was judged. Reversing a debit does not re-run a decision that has already been
  communicated.

A reversal reverses an entry. It does not reverse history, and a system that claimed otherwise
would be one where a customer's statement changes after they have read it.

*Refuted by* `RejectedCriteriaTest.reversalDoesNotRestorePriorValues`.

## 3. "Three instalments of 3.334"

Three of 3.334 is **10.002**. The instruction was for 10.000, so this over-credits by 0.002.

Three of 3.333 is 9.999, which under-credits by 0.001. Neither uniform split is the amount
instructed, because ten does not divide by three in any number of decimal places.

The correct split at three decimals is **3.333, 3.333, 3.334**. It works in minor units, where
division is exact integer arithmetic, and hands the single leftover unit to the last part. The
parts sum to the total by construction rather than by luck.

*Refuted by* `RejectedCriteriaTest.theInstalmentSplitIsNotUniform` and
`RemainderAllocatorTest`.

## 4. "If the rounded accruals do not sum to the capitalised total, discard the remainder"

This directly contradicts the rule it appears beside, which requires the daily accruals to sum
exactly to the capitalised total. Discarding a remainder is precisely how they stop summing.

It is also the wrong direction. The remainder is real money: over a thousand days at a balance
accruing 0.0051 a day, the difference between carrying and discarding is 4.90 against 5.10
actually owed. At volume that is a profit-and-loss line, not a rounding artefact.

The remainder is carried. Each day publishes the correctly rounded running total less what has
already been published, so the daily figures sum exactly by construction and nothing is dropped.

*Refuted by* `RejectedCriteriaTest.theRemainderCannotBeDiscarded` and
`InterestAccrualPolicyTest.longHorizonDrift`.

---

# Approaches abandoned during the build

What was tried, why it was dropped, and what dropping it cost. Recorded as each happened; the
timestamped account is in `WORKLOG.md`.

## A dependency manager, and JUnit with it

Maven Central was unreachable from the environment this was built in — the egress proxy
refused `repo.maven.apache.org` and `repo1.maven.org` outright — so JUnit could not be
fetched.

Rather than work around the network, the project took zero external dependencies and a small
test harness of its own. For something another person has to run, that turned out to be the
better answer regardless: no download step, no dependency resolution, no version drift, and a
JDK is the whole prerequisite.

**The cost is real.** No parameterised tests, so a table-driven case is a loop written by hand.
No automatic discovery, so `AllTests` lists every suite explicitly and adding one means
remembering to register it. No assertion library beyond equality, truth and "this should
throw".

## A bootstrap compiler driven through the compiler API

The first image had a JRE layout: `java` was present, `javac` was not, but the in-process
compiler API was reachable. Building on that was rejected. It would have been a clever solution
to a problem nobody else has — a normal machine has `javac` — and every future reader would
have had to understand the workaround before understanding the ledger. A JDK was installed
instead and `run.sh` stayed conventional.

## A single `Money` factory

One factory could not do both jobs. `Money.of("1.005", AED)` is a typo and must fail loudly
rather than silently becoming 1.01; a computed interest accrual legitimately needs rounding and
must not throw. Attempting both in one method meant choosing which of the two callers to
mislead.

Split into `of` for literals, which refuses input finer than the currency, and `round` for
computed values, which rounds because the caller asked. That split is what lets an interest
accrual round exactly once, at the point it is published.

## A mutable balance field on an account

Dropped as soon as it was written down. A balance is a pure function of the entry list; a
stored balance needs a setter, and append-only stops being structural the moment one exists.
`Account` has an identity, a currency and an opening day, and no total.

## A `REVERSAL` entry type

The first sketch gave reversals their own entry type, and it does not work: the direction of a
reversal depends on the entry it reverses, so a signed amount could not be derived from the
type alone. Reversing a debit books a **credit** — the money genuinely does come back — and the
fact that the credit is a correction is recorded as a link back to the original. Direction stays
a property of the type and amounts stay positive.

## A CSV or text stream format

Considered and dropped. A parser would have bought scenario authoring that is not needed twice
here, and cost a grammar, its error handling and its own test suite. The typed builder keeps the
compiler as the validator: a malformed event does not compile.

## A shared interest policy instance

The interest policy carries a running remainder per account. Configured as an instance rather
than a factory, two engines built from one configuration would have shared it, and the second
replay would have continued the first replay's carry — publishing 1.64 where the answer is 0.82,
for identical input. The determinism requirement is what surfaced it. The configuration hands
out a fresh policy per replay, which makes the mistake unavailable rather than merely detected.

## A fee event id keyed by account and day

Fee entries took their source id from the account and the day being closed. That is unique only
while a close books at most one fee, and it was written when no other reading was implemented.
`RestatementFeePolicy` books three fees in a single close, all of which would have shared an
id, so a later reversal naming that id could not have said which fee it meant. The value day is
now part of the id.

A one-fee-per-day assumption had been baked into an identifier without anyone deciding it, and
only a second policy could have found it.

## Charging the overdraft fee in a currency it was not configured for

The fee is given in dirhams and one account is in dinars. Applying the same number to both would
assert that 25.00 dirhams and 25.000 dinars are the same charge; they differ by roughly a factor
of ten. Skipping the fee silently would understate what is owed.

Neither. Fees are configured per currency and one falling due in an unconfigured currency
throws. It never fires in this scenario, because that account never goes negative — which is the
point. Nothing was invented to cover a case the rules do not describe.
