#!/usr/bin/env bash

set -e
set -x

INPUT_LOG="$1"

OUTPUT_PREFIX="./data/online"

POLICY_PREFIX="./policy-monitoring/mfotl-policies"

SIGNATURE="$POLICY_PREFIX/predicates.sig"

declare -a POLICIES=("clean_logs" "reboot_count" "logging_behavior__exe" "unauthorized_connections")
for pol in "${POLICIES[@]}"
do
	echo "Running online simulation for policy $pol ..."
	policy_dir="$POLICY_PREFIX/$pol"
        formula="$policy_dir/formula.mfotl"        
	out_dir="$OUTPUT_PREFIX/$pol"
	mkdir -p "$out_dir"
	/usr/bin/time -v ./experiments/simulate_online.sh \
	    "$INPUT_LOG" \
	    "$out_dir/out.txt" \
	    "$out_dir/report.txt" \
	    -sig "$SIGNATURE" \
	    -no_rw \
	    -formula "$formula"
done

python3 ./experiments/make_plot.py
