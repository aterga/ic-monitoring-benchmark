#!/usr/bin/env bash

set -e
set -x

INPUT_LOG="./data/online/production.log"

OUTPUT_PREFIX="./data/online"

POLICY_PREFIX="./policy-monitoring/mfotl-policies"
SIGNATURE="$POLICY_PREFIX/predicates.sig"

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections")

if [[ -n "$1" ]]; then
  declare -a RUN_POLICIES=("$@")
else
  declare -a RUN_POLICIES=("${POLICIES[@]}")
fi

for pol in "${RUN_POLICIES[@]}"
do
	echo "Running online simulation for policy $pol ..."
	policy_dir="$POLICY_PREFIX/$pol"
	formula="$policy_dir/formula.mfotl"
	out_dir="$OUTPUT_PREFIX/$pol"
	mkdir -p "$out_dir"
	bash ./experiments/simulate_online.sh \
	    "$INPUT_LOG" \
	    "$out_dir/out.txt" \
	    "$out_dir/report.txt" \
	    -sig "$SIGNATURE" \
	    -no_rw \
	    -formula "$formula"
done

python3 ./experiments/make_plot.py
