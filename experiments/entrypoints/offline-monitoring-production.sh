#!/usr/bin/env bash

set -e
set -x

if [[ "$1" == "-l" ]]; then
  INPUT_LOG="$2"
  shift 2
else
  INPUT_LOG="./data/production.raw.log"
fi

GLOBAL_INFRA="./data/mercury-reg-snap--20220905_212707.json"

OUTPUT_PREFIX="./data/offline/production"

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections")

if [[ -n "$1" ]]; then
  declare -a RUN_POLICIES=("$@")
else
  declare -a RUN_POLICIES=("${POLICIES[@]}")
fi

for pol in "${RUN_POLICIES[@]}"
do
    echo "Running offline monitoring (on production logs) for policy $pol ..."
    python3 ./policy-monitoring/main.py \
        --mode universal_policy save_event_stream \
        --read "$INPUT_LOG" \
        --global_infra "$GLOBAL_INFRA" \
        --artifacts "../$OUTPUT_PREFIX" \
        --formulas_for_preproc "${POLICIES[@]}" \
        --policy "$pol" \
        --hard_timeout_seconds 14400
done

python3 ./experiments/make_table.py "$INPUT_LOG" > ./data/offline/results.txt
