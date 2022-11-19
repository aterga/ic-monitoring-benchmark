#!/usr/bin/env bash

set -e
set -x

INPUT_LOG="$1"  # e.g., "jmeisfhcqxtgy"
GLOBAL_INFRA="$2"

OUTPUT_PREFIX="../data/offline/production"  # relative to policy-monitoring

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections")

for pol in "${POLICIES[@]}"
do
    echo "Running offline monitoring (on production logs) for policy $pol ..."
    python3 ./policy-monitoring/main.py \
        --mode universal_policy save_event_stream \
        --read "$INPUT_LOG" \
        --global_infra "$GLOBAL_INFRA" \
        --artifacts "$OUTPUT_PREFIX" \
        --formulas_for_preproc "${POLICIES[@]}" \
        --policy "$pol" \
        --hard_timeout_seconds 14400
done
