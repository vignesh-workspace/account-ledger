# Ambiguities

Where the requirements admit more than one reading. Each entry states the ambiguity, the
readings considered, the resolution, what breaks if the resolution is wrong, and the test that
holds it in place. An entry resolved only in prose is weaker than one resolved by an assertion,
so every entry here names a test.

---

## 1. A balance is not a function of one date

**The ambiguity.** "The closing balance of day two" has no single answer. Every entry carries a
value day and a booking day, and an instruction can arrive days after the day it takes value
from.

**Readings.** Balance as reported on the day. Balance as it stands with everything known now.

**Resolution.** Both, and the caller must say which. `balanceAsOf(account, valueDay,
knowledgeDay)` requires both dates and there is no shorter overload. Day two is 250.00 asked on
day two and −370.00 asked on day five, and both are correct answers to different questions.

**If this is wrong,** every restated figure is either unreachable or silently wrong, and no
caller can tell which they got.

**Test.** `LedgerBookTest.balanceIsBitemporal`, `ScenarioDecisionsTest.dayTwoRestatedFromDayFive`.

---

## 2. Is the fee inside the balance the fee rule reads?

**The ambiguity.** The fee is charged when the closing balance is negative, and the fee itself
changes the closing balance.

**Readings.** Test the pre-fee balance. Test after booking, which can charge a second fee for a
balance the first fee caused.

**Resolution.** Test the balance before any fee is booked, book the fee, then accrue interest on
what is left. The stage order is a `final` method so it cannot be rearranged.

**If this is wrong,** an account can be charged twice for one overdraft, or interest can be paid
on money that has already been taken.

**Test.** `FlatOverdraftFeePolicyTest.assessmentReadsThePreFeeBalance`, `ScenarioReplayTest.dayFive`.

---

## 3. Does a partial settlement close the authorization?

**The ambiguity.** A hold of 200.00 settles for 185.00. The remaining 15.00 is either released
or still reserved.

**Readings.** Close and release the difference. Keep holding it against a further settlement.

**Resolution.** Close and release. The merchant has said what the transaction was worth;
continuing to reserve the rest restricts a customer's funds against an instruction that has
already completed.

**If this is wrong,** funds stay reserved after the transaction they were reserved for is over,
and a subsequent authorization can be declined against money nothing is going to claim.

**Test.** `ScenarioReplayTest.dayFour` — holds fall to zero and available returns to 465.00.

---

## 4. Are refused instructions recorded?

**The ambiguity.** Whether a declined authorization or an unmatched settlement leaves any trace.

**Readings.** Refusals are errors, raised and discarded. Refusals are records.

**Resolution.** Records. Every instruction produces exactly one outcome and the day report
prints all of them. Nothing is thrown for a business refusal, so a bad instruction cannot
discard the rest of the stream.

**If this is wrong,** a day on which three instructions were declined reads as an empty day, and
nobody can answer why a payment did not arrive.

**Test.** `OutcomeTest.acceptanceIsStructural`, `EngineModesTest.duplicateEventIdIsRefused`.

---

## 5. Zero is neither positive nor negative

**The ambiguity.** A balance of exactly zero, against rules phrased as "negative" and "positive".

**Readings.** Zero counts as one or the other. Zero is neither.

**Resolution.** Neither. A zero balance draws no overdraft fee and earns no interest. Both
follow from the same reading, and a rule that meant otherwise would have had to say so.

**If this is wrong,** an emptied account is charged for being empty, or paid for it.

**Test.** `FlatOverdraftFeePolicyTest.zeroIsNotOverdrawn`,
`InterestAccrualPolicyTest.onlyPositiveBalancesEarn`.

---

## 6. An instruction that arrives after a later day has closed

**The ambiguity.** The stream is not in booking-day order: a reversal booking on day six is
listed before a credit booking on day five.

**Readings.** Bucket and close — group by booking day, process days ascending. Strict arrival
order — the stream is a tape, days close as it passes, and anything dated earlier is late.

**Resolution.** Bucket and close by default, with strict arrival order available as
configuration. Both are implemented and the strict-mode test proves it refuses the day-five
credit that the default accepts.

**If this is wrong,** either instructions are silently backdated into closed days, or legitimate
ones are refused for the order somebody wrote a file in.

**Test.** `EngineModesTest.strictModeRefusesALateArrival`, `EngineModesTest.bucketModeAcceptsTheSameInstruction`.

---

## 7. Order within a day

**The ambiguity.** Whether the order of instructions inside a day is meaningful or incidental.

**Readings.** Sort into a canonical order. Treat the given order as authoritative.

**Resolution.** Authoritative, and never sorted anywhere. The stream builder preserves
submission order exactly.

**If this is wrong,** answers change while the code looks like it is tidying up. In this
scenario an authorization moved one position earlier in the stream is approved rather than
declined, with nothing else altered.

**Test.** `ScenarioDecisionsTest.streamOrderDecidesTheAnswer`,
`EventStreamTest.submissionOrderIsPreserved`.

---

## 8. Hold expiry is undefined

**The ambiguity.** No rule says when an authorization expires, and the window ends with one
authorization never settled.

**Readings.** Invent an expiry period. Leave holds live indefinitely. Model expiry as reachable
but unreached.

**Resolution.** The state exists and nothing in this window produces it. Inventing a period
would be a number with no source; omitting the state entirely would imply an authorization can
stay live forever, which is the one answer that is certainly wrong.

**If this is wrong,** funds stay reserved past any reasonable point with no rule to release
them, and the report has no vocabulary for saying so.

**Test.** `HoldRegistryTest.terminalStatesAreNotInterchangeable` — every terminal state is
distinct and none collapses into another.

---

## 9. The interest rules contradict each other

**The ambiguity.** Each day publishes a rounded accrual, and the daily accruals must sum
exactly to the capitalised total. Over the scenario those give 0.83 and 0.82.

**Readings.** Define the total as the sum of rounded days (0.83). Round the true total and let
the daily figures not sum (0.82, breaking a stated rule). Carry the remainder between days.

**Resolution.** Carry the remainder. Each day publishes the correctly rounded running total less
what has already been published, so the figures sum by construction. See `NUMBERS.md`.

**If this is wrong,** interest drifts upward by a fraction of a minor unit per account per day,
which is a real profit-and-loss line at volume.

**Test.** `InterestAccrualPolicyTest.publishedAccrualsSumToTheCapitalisedTotal` and
`.longHorizonDrift`, which measures the alternative publishing 10.00 against 5.10 owed.

---

## 10. Days have no calendar

**The ambiguity.** The requirements name days as "Day 1" through "Day 6" with no anchor to a
real date.

**Readings.** Map onto real dates. Keep them ordinal.

**Resolution.** Ordinal. A date type would force inventing a timezone, a weekend rule and a
holiday calendar that no requirement mentions, and each of those would then be wrong in a way
nobody asked for.

**If this is wrong,** the ledger cannot express value dating against a real banking calendar —
which is named as a cut rather than approximated.

**Test.** `BusinessDayTest` — ordering and rendering are by ordinal alone.

---

## 11. Entries are single-sided

**The ambiguity.** No contra account is given, so nothing structurally sums to zero.

**Readings.** Invent a contra account to enable double entry. Accept single-sided entries and
find another check.

**Resolution.** Single-sided, with a conservation check instead: each account's entries must sum
to its final balance. Inventing the other side of every entry would be modelling a bank's whole
chart of accounts on the strength of no requirement at all.

**If this is wrong,** the ledger has no structural defence against money appearing from nowhere,
which is why the narrower check runs in the suite and in the harness.

**Test.** `DeterminismTest.nothingCreatesMoney`.

---

## 12. Closing an account versus deleting one

**The ambiguity.** What "closed" means for the entries an account already has.

**Readings.** Remove the account and its history. Close it to new entries and keep everything.

**Resolution.** Close and keep. Closure is an event, so it lands in the journal and replays;
there is no deletion operation at all. The entries of a closed account are still true statements
about the days it was open.

**If this is wrong,** a statement for a day the account was open stops being producible, and
history becomes dependent on the present.

**Test.** `EngineModesTest.closureIsJournalledAndEnforced` — later entries are refused, the
balance is kept, and live holds are released.

---

## 13. A settlement with no matching authorization

**The ambiguity.** Whether an unmatched settlement is refused or honoured as a forced posting.

**Readings.** Refuse it. Accept it, as card schemes do accept force-posts.

**Resolution.** Refuse it, and record the refusal. This is stricter than a real scheme, and it
is a policy choice rather than an oversight: honouring a settlement against an authorization
that was never approved moves money on the strength of an identifier nobody issued.

**If this is wrong,** legitimate late settlements are rejected and have to be re-presented.

**Test.** `ScenarioDecisionsTest.unmatchedSettlementIsRefused` — the funds do not move.

---

## 14. Is capitalised interest inside the final day's closing balance?

**The ambiguity.** The interest credit is dated the last day, and the last day has a closing
balance.

**Readings.** Inside it, so the day closes at 440.82. After it, so the day closes at 440.00 and
the final balance is 440.82.

**Resolution.** After. The final day closes on its own trading, and the capitalisation is
visible as the separate thing it is. The summary prints the closing balance, the capitalised
amount and the final balance, so the three reconcile in plain sight.

**If this is wrong,** the last day's closing balance includes something that is not trading, and
no column shows what the day itself did.

**Test.** `ScenarioReplayTest.finalPosition`.

---

## 15. An unknown account: refuse it, or fail?

**The ambiguity.** The requirements describe unknown accounts both as an ingest rejection and as
something that throws.

**Readings.** Always throw. Always refuse. Both, at different moments.

**Resolution.** Both. An instruction naming an account that was never opened is bad input, so
ingest refuses it and the day report records it; crashing would discard every other instruction
in the stream. Past ingest the same lookup throws, because an unknown account has stopped being
bad input and become a defect in the engine that let it through.

**If this is wrong,** either one malformed instruction destroys a whole run, or a genuine engine
defect is quietly reported as a business refusal.

**Test.** `AccountRegistryTest.requireThrowsForUnknownAccount`, and the ingest checks in
`Replayer.apply`.

---

## 16. Interest on an account that closes mid-window

**The ambiguity.** An account accrues interest, then closes before the window ends. The
capitalisation credit is booked at the end of the window.

**Readings.** Drop the accrual. Book the credit to the closed account. Capitalise at closure.

**Resolution.** Book it. The interest was earned while the account was open and is owed. This
does mean the ledger books an entry to a closed account, which sits awkwardly beside the rule
that a closed account takes no further entries — that rule governs instructions, not the
ledger's own obligations. Capitalising at closure is the better answer and is not built.

**If this is wrong,** money that was genuinely earned is silently discarded when an account
closes.

**Test.** `EngineModesTest.closureIsJournalledAndEnforced` — the account closes on day three and
still finishes at 500.40.

---

## 17. Which value day does a reversal take?

**The ambiguity.** A reversal names its own value day, and the entry it reverses has one too.

**Readings.** Force the original's, making the field on the event decorative. Trust the
instruction.

**Resolution.** Trust the instruction. The reversal's own value day is used, exactly as stream
order is trusted. In the scenario the two agree, and the correction restates the same day the
error affected.

**If this is wrong,** a correction can land on a day the error never touched, restating two days
instead of none.

**Test.** `LedgerBookTest.reversalAppendsRatherThanMutates`, `ScenarioReplayTest.daySix`.

---

## 18. A settlement larger than its hold

**The ambiguity.** Nothing says a settlement cannot exceed the amount reserved.

**Readings.** Refuse the excess. Book what actually settled.

**Resolution.** Book it, and say so in the report, which names the amount by which the hold was
exceeded. The settled amount is what moved; a hold is a reservation, not a ceiling on what a
completed transaction turned out to be worth.

**If this is wrong,** genuine over-captures are refused and have to be re-presented, or an
account is overdrawn by an amount no authorization anticipated.

**Test.** The under-capture path is exercised by `ScenarioReplayTest.dayFour`; the excess is
reported by `Replayer.settle`.
