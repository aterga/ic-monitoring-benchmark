from datetime import datetime
import re
import sys

PRELUDE_GAP = 10000

ts0_pattern = re.compile(r"@0 ")
ts_pattern = re.compile(r"@([0-9]+) ")

[in_name, out_name] = sys.argv[1:3]
with open(in_name, 'r') as in_file:
    buffer = []
    lines = iter(in_file)
    try:
        while True:
            line = next(lines)
            if ts0_pattern.match(line):
                buffer.append(line)
            else:
                match = ts_pattern.match(line)
                first_ts = int(match.group(1))
                prelude_ts = first_ts - PRELUDE_GAP
                print(f"Found {len(buffer)} prelude lines")
                print(f"First proper time-stamp: {first_ts} ({datetime.fromtimestamp(first_ts/1000)})")
                print(f"New prelude time-stamp: {prelude_ts} ({datetime.fromtimestamp(prelude_ts/1000)})")
                print(f"Writing to output ...")
                with open(out_name, 'w') as out_file:
                    for pline in buffer:
                        out_file.write(ts0_pattern.sub(f"@{prelude_ts} ", pline, count=1))
                    out_file.write(line)
                    while True:
                        out_file.write(next(lines))
    except StopIteration:
        pass

