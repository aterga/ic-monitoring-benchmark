#!/usr/bin/env bash

set -e
set -x

INPUT_DIR="$1"  # e.g., "jmeisfhcqxtgy"

OUTPUT_PREFIX="./data/offline/system-tests"

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections" "finalization_consistency" "finalized_height" "replica_divergence" "block_validation_latency")

for pol in "${POLICIES[@]}"
do
    echo "Running offline monitoring (on system test logs) for policy $pol ..."
    python3 ./policy-monitoring/main.py \
        --mode universal_policy save_event_stream \
        --read "$INPUT_DIR" \
        --artifacts "$OUTPUT_PREFIX" \
        --formulas_for_preproc "${POLICIES[@]}" \
        --policy "$pol" \
        --hard_timeout_seconds 14400
done
