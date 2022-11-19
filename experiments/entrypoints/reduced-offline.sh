#!/usr/bin/env bash

set -e

./experiments/entrypoints/offline-monitoring-system-tests.sh clean_logs reboot_count
./experiments/entrypoints/offline-monitoring-production.sh clean_logs reboot_count
python3 ./experiments/make_table.py > ./data/offline/results-reduced.txt

echo "Done!"
