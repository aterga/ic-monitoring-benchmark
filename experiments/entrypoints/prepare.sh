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

OUTPUT_PREFIX="./data/online/temp"
PRODUCTION_LOG="./data/online/production.log"

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections")

echo "Preprocessing the production log for online monitoring ..."
# TODO: Is this the correct command?
python3 ./policy-monitoring/main.py \
    --mode save_event_stream \
    --read "$INPUT_LOG" \
    --global_infra "$GLOBAL_INFRA" \
    --artifacts "../$OUTPUT_PREFIX" \
    --formulas_for_preproc "${POLICIES[@]}"

echo "Further preprocessing the production log ..."
find "$OUTPUT_PREFIX" -name '*.unipol.log' -exec \
    python3 ./experiments/make_online.py '{}' "$PRODUCTION_LOG" ';' \
    -quit

echo "Computing index rate graph for production log ..."
monpoly -sig policy-monitoring/mfotl-policies/predicates.sig \
    -formula experiments/index_rate.mfotl \
    -log "$PRODUCTION_LOG" > data/online/production_index_rate.txt

echo "Done!"
