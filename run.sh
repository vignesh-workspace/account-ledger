#!/usr/bin/env bash
# Builds and runs the ledger core. No network, no dependency manager: a JDK is all it needs.
set -euo pipefail
cd "$(dirname "$0")"

OUT=build/classes
rm -rf build && mkdir -p "$OUT"

echo "compiling..."
find src/main/java src/test/java -name '*.java' > build/sources.txt
javac -Xlint:all -d "$OUT" @build/sources.txt

case "${1:-test}" in
  test)   echo "running test suite..."; java -cp "$OUT" com.accountledger.AllTests ;;
  report) echo "running replay harness..."; java -cp "$OUT" com.accountledger.LedgerHarness ;;
  all)    java -cp "$OUT" com.accountledger.AllTests && echo && java -cp "$OUT" com.accountledger.LedgerHarness ;;
  *)      echo "usage: ./run.sh [test|report|all]" >&2; exit 2 ;;
esac
