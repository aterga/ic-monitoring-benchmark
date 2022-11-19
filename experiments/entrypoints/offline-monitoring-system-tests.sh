#!/usr/bin/env bash

set -e
set -x

if [[ "$1" == "-l" ]]; then
  INPUT_DIR="$2"
  shift 2
else
  INPUT_DIR="./data/system-tests"
fi

OUTPUT_PREFIX="./data/offline/system-tests"

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections" "finalization_consistency" "finalized_height" "replica_divergence" "block_validation_latency")

if [[ -n "$1" ]]; then
  declare -a RUN_POLICIES=("$@")
else
  declare -a RUN_POLICIES=("${POLICIES[@]}")
fi

for pol in "${RUN_POLICIES[@]}"
do
    echo "Running offline monitoring (on system test logs) for policy $pol ..."
    python3 ./policy-monitoring/main.py \
        --mode universal_policy save_event_stream \
        --read "$INPUT_DIR" \
        --artifacts "../$OUTPUT_PREFIX" \
        --formulas_for_preproc "${POLICIES[@]}" \
        --policy "$pol" \
        --hard_timeout_seconds 14400
done
