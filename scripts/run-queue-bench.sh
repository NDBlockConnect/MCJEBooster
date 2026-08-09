#!/usr/bin/env bash
# Runs the v26.0-Alpha.6 scheduling-strategy benchmark against the
# latest local build. Builds first if target/classes is missing.
#
# Usage: ./scripts/run-queue-bench.sh [regions] [hotRegions] [hotNanos] [coldNanos] [rounds] [warmup] [workers]
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -d target/classes || ! -d target/classes/com/mcjebooster ]]; then
  bash scripts/build.sh --skip-tests
fi

CP="target/classes;lib/asm-9.6.jar;lib/asm-tree-9.6.jar;lib/asm-commons-9.6.jar;lib/json-20231013.jar"
if [[ "$OSTYPE" != "msys" && "$OSTYPE" != "cygwin" ]]; then
  CP="$(echo "$CP" | tr ';' ':')"
fi

exec java -cp "$CP" com.mcjebooster.benchmark.QueueBenchmark "$@"
