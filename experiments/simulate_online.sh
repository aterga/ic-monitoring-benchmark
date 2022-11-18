#!/bin/sh

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 log-file output report-file [monpoly-args...]"
  exit 1
fi

input="$1"
output="$2"
report="$3"
shift 3

ulimit -Ss 65536
replayer -a 1000 -q 2000 -i monpoly -f monpoly --latency-report 600 "$input" 2> "$report" \
  | /usr/bin/time -v monpoly "$@" > "$output"
