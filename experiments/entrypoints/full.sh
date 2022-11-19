#!/usr/bin/env bash

set -e

./experiments/entrypoints/offline-monitoring-system-tests.sh
./experiments/entrypoints/offline-monitoring-production.sh
python3 ./experiments/make_table.py > ./data/offline/results.txt

./experiments/entrypoints/online-monitoring.sh
python3 ./experiments/make_plot.py

echo "Done!"
