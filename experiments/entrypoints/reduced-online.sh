#!/usr/bin/env bash

set -e

./experiments/entrypoints/online-monitoring.sh clean_logs
python3 ./experiments/make_plot.py

echo "Done!"
