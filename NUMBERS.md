# Numbers

Every constant in the system, in two groups. The first group was given and is not defended.
The second was chosen, and each choice is defended against the obvious alternative — because a
choice that cannot survive someone proposing the opposite was not really made.

---

## Given by the specification

No argument is offered for these. They are inputs.

| Value | Where it appears |
|---|---|
| Overdraft fee, 25.00 in dirhams | Configuration, per currency |
| Daily interest rate, 0.04% (0.0004) | Configuration |
| Two decimal places for dirhams, three for dinars | Read from `java.util.Currency`, never declared |
| A six-day window | Configuration |
| Opening balances of zero on both accounts | Configuration |
| Three instalments | The instruction that expands into them |

**Currency precision is derived, not declared.** There is no `AED_SCALE = 2` anywhere and
there must never be one. Scale comes from `Currency.getDefaultFractionDigits()`, verified
against the JDK: dirhams 2, dinars 3, Kuwaiti dinars 3, yen 0. An account in yen would round
fees and interest to whole yen with no code change and no new constant.

---

## Chosen, and why

### HALF_UP rather than HALF_EVEN

Banker's rounding exists to stop a long run of roundings drifting upward, and it is better
than HALF_UP at exactly that. It is not used here, for two reasons.

The drift it prevents is already prevented. The remainder allocator distributes leftovers
rather than discarding them, and the interest carry publishes the correctly rounded running
total rather than a sequence of independent roundings. Adding HALF_EVEN on top would be a
second mechanism for a problem that already has one, and two mechanisms for one problem is
how a system acquires a bug that only appears when they disagree.

The second reason is auditability. Asked why 0.125 became 0.12 while 0.135 became 0.14, the
honest HALF_EVEN answer involves the parity of the preceding digit. HALF_UP always rounds a
half away from zero, which anyone can check by hand. For a ledger someone has to explain to a
customer, that is worth more than statistical neutrality that the allocator is already
delivering.

### DECIMAL128 for intermediate arithmetic

Thirty-four significant digits for values that have not yet been rounded to a currency scale.
DECIMAL64, at sixteen digits, is enough for this scenario, and that is the problem with it:
it is enough here and silently insufficient somewhere else. A forty-year accrual on a
nine-figure balance is not an exotic case for a bank, and the failure mode is a wrong number
rather than an exception. The cost of the wider context is arithmetic nobody will measure.

### Global gapless sequence numbers from 1

Entries are numbered across the whole book rather than restarting each day. Instructions
arrive out of booking-day order — the scenario contains a reversal booking on day six listed
before a credit booking on day five — so ordering has to be total across days, not merely
within one. Per-day numbering would make "entry 3" ambiguous and would need the day carried
alongside it everywhere to disambiguate, which is the global number with extra steps.

Gapless, because a gap in a ledger's numbering is a question an auditor is entitled to ask.
Rejected instructions consume no sequence number: they book no entry, and the number is a
property of the entry rather than of the attempt.

### Simple interest, not compound

Nothing capitalises before the end of the window, so through the whole window there is
nothing to compound on. Compounding would be arithmetic with no observable effect, defended
by an argument about what might happen in a longer window that this system does not model.

### The remainder goes to the last parts

Ten dinars split three ways is 3.333, 3.333, 3.334 — the extra minor unit on the last part
rather than the first. Both are defensible and neither is more correct. What matters is that
the choice is fixed and written down, because an allocator that varies its answer makes a
ledger irreproducible, and irreproducible is a worse property than mildly arbitrary.

The same discipline handles the interest remainder, one day at a time instead of one part at
a time. They are the same problem, so they get one implementation and one set of tests.

### Interest publishes the running rounded total, less what was already published

The rules require both that each day publishes a rounded accrual and that the daily accruals
sum exactly to the capitalised total. Over the scenario those two requirements give 0.83 and
0.82 respectively, so one of them has to give way explicitly. The alternatives:

| Reading | Total | Why not |
|---|---|---|
| Round each day, define the total as their sum | 0.83 | Drifts upward systematically; a real profit-and-loss line at volume |
| Round the true total, leave the daily figures not summing | 0.82 | Contradicts a stated rule |
| Publish `round(cumulative) − published so far` | 0.82 | Chosen |

The visible cost: the final day publishes 0.17 where `440.00 × 0.0004` rounded on its own is
0.18. That is the carry doing its job, and both numbers are asserted in the same test so the
difference cannot be quietly "corrected" later.

### The fee is configured per currency, with no conversion

One fee amount applied to every currency would assert that 25.00 in dirhams and 25.000 in
dinars are the same charge. They differ by roughly a factor of ten. A fee falling due in a
currency with no configured amount throws rather than inventing a rate or silently charging
nothing: both of those produce a number, and a wrong number is worse than a stopped run.

### Amounts are always positive

Direction is carried by the event kind and the entry type, never by the sign of an amount. A
signed amount admits "a debit of minus five", which is a contradiction the type system would
otherwise have to be told about at every call site. The single place the convention is
applied is `LedgerEntry.signedAmount()`.

Zero is neither positive nor negative, and both halves of that matter: a zero balance earns
no interest and attracts no overdraft fee.
