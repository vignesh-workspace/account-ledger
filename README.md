# Account Ledger

An in-memory, append-only account ledger core in Java. It replays a stream of dated
instructions and reports, for each day, the closing balance of every account, the fees
assessed, the state of every authorization, and the instructions it refused.

There is no database, no web layer and no user interface. There are also no dependencies: a
JDK is the entire prerequisite.

## Running it

```bash
./run.sh test      # the test suite
./run.sh report    # replay the scenario and print the report
./run.sh all       # both
```

On Windows use Git Bash. `run.sh` compiles with `javac` into `build/` and runs the result;
there is no build tool to install and nothing to download.

**Prerequisite:** a JDK, version 21 or later. Java 21 is required for exhaustive pattern
matching over sealed interfaces, which is how events are dispatched.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Everything passed, or the replay reconciled |
| 1 | A test failed, or an account did not reconcile against its own entries |
| 2 | `run.sh` was given an argument it does not recognise |

A known-failing test does **not** fail the build. A known-failing test that starts passing
does: the design would have changed without anyone recording it.

## Reading the report

The report prints one block per day.

**Instructions.** Every instruction scheduled for that day, each with what became of it. A
line beginning `OK` moved money or placed a hold; a line beginning `REFUSED` did neither and
names the reason. A refusal is a record, not a silence — a day on which three instructions
were declined is not an empty day.

**Positions.** One row per account:

| Column | Meaning |
|---|---|
| closing | Balance of all entries valued on or before this day, as known on this day |
| holds | Funds reserved by live authorizations |
| available | closing less holds |
| fee | Overdraft fee assessed at this day's close |
| interest | Interest published for this day |

Where a fee was charged, a second line shows the balance before and after it. The fee is
tested against the balance before it is booked; interest accrues on what is left afterwards.

**Authorizations.** Every authorization approved so far and its current state. Finished ones
stay listed: an authorization that vanished on settling would make the day it settled look
like the day it never existed.

At the end of the window, the summary shows each account's final-day closing balance, the
interest capitalised, and the final balance. Capitalisation is applied *after* the last day
closes, so those first two columns add up to the third.

The report closes with a conservation check: every account's entries must sum to its final
balance. Entries here are single-sided — a credit has no matching debit anywhere — so the
ledger does not net to zero and double entry cannot prove it consistent. This narrower claim
can be proved, and it is the guard against a bug that creates money.

## Two things worth knowing before reading the numbers

**A balance needs two dates.** Every entry carries a value day, the day the money belongs to,
and a booking day, the day the ledger learned about it. An instruction can arrive days after
the day it takes value from, which means a balance already reported can be a different number
when asked again later. `LedgerBook.balanceAsOf` therefore requires both a value day and a
knowledge day, and offers no shorter form.

**A published daily accrual is not always that day's balance times the rate.** The rules
require the daily figures to sum exactly to the capitalised total, so each day publishes the
correctly rounded running total less what has already been published, and the remainder
carries forward. In the scenario this makes the final day publish 0.17 where the isolated
calculation gives 0.18.

## The documents

| File | Contents |
|---|---|
| `NUMBERS.md` | Every constant: what was given, and what was chosen and why |
| `AMBIGUITIES.md` | Where the requirements admit more than one reading, how each was resolved, and the test that pins it |
| `REJECTED.md` | Acceptance criteria that are arithmetically wrong, and approaches abandoned during the build |
| `ARCHITECTURE.md` | Structure, what breaks at scale, and what was deliberately cut |
| `WORKLOG.md` | Decisions and reversals, timestamped |
