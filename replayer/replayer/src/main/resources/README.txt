Usage: replayer.sh [options...] [file]

Options:
    -h, --help  Display this information.

    -v          Writes a compact report every second to stderr (see below).

    -vv         Writes a verbose report every second to stderr.

    --latency-report <timeout>
                Writes latency statistics every second to stderr. See below
                limitations regarding accuracy. Once the observed latency
                exceeds the given timeout value (in seconds), the replayer
                terminates with exit code 10. Overrides -v and -vv.

    -a <acceleration>
                Specifies the acceleration factor (default: 1). For example,
                a value of 2 will replay the trace twice as fast. Time-stamps in
                the input are interpreted as seconds; if your log uses
                milliseconds, for example, set the acceleration to 1000 to get
                a real-time stream. Set it to 0 to replay the whole trace as
                quickly as possible.

    -i <format> Input format. See below for supported formats.

    -f <format> Output format. See below for supported formats.

    -m <linear> Output in MonPoly format. If <linear> is true, events will be
                linearized into separate time-points.

    -d <linear> Output in DejaVu format. If <linear> is true, events will be
                linearized into separate time-points.

    --force-quotes
                For MonPoly output: Enclose all values in quotes.

    -t <interval>
                Injects the current time into the output at regular intervals.
                The interval is specified in milliseconds. The format of the
                timestamp lines is "<prefix><ts>", where <ts> is a Unix
                timestamps with millisecond resolution. By default, no
                timestamps are injected. The <prefix> can be changed with -T
                <prefix> (default: "###").

    -q <buffer size>
                Sets the size of the internal buffer in chunks of 128 events
                between the reader and writer thread (default: 1024). Increase
                this value if -vv repeatedly reports underruns.

    -n <number of sources>
                Number of different input sources. Requires output to Kafka.

    -e          Use explicit emission time. If set, every input line must begin
                with a number followed by a single quote "'". The number is
                interpreted as the desired emission time (in seconds, modulo
                acceleration) relative to the replayer's start.

    --term {NO_TERM, TIMESTAMPS, TIMEPOINTS}
                Controls the generation of terminators in the parser.
                NO_TERM = parser just parses the trace as is
                TIMESTAMPS = parsers adds terminators after a new time-stamp
                TIMEPOINT = parsers adds terminators after a new time-point

    -nt, -no-end-marker
                Replayer does not print end markers (i.e., terminators).

    -o kafka    Write output to Kafka topic "monitor_topic" in group "monitor".
                The server is currently hardwired to 127.0.0.1:9092.

    --clear     Clears the Kafka topic before replaying.

    -o <host>:<port>
                Opens a TCP server listening on the given host name and port.
                Only a single client is accepted, to which the output is
                written. If this option is not given, writes to stdout.

    -k          Allow TCP client to reconnect.

    -C <prefix> Sets the prefix for command lines (default: ">"). Lines that
                start with this prefix are sent to the output unmodified.

Supported formats for reading and writing:
    csv     CSV format from the First International Competition on Software for
            Runtime Verification (CRV 2014). This is the default.
    monpoly MonPoly's trace format.
    dejavu  DejaVu's trace format.

If no file name is given after the options, events are read from stdin.

The replayer reads events from a trace or stream in the specified format. The
replayer reproduces these events as a real-time stream according to the events'
timestamps. The events for the first timestamp are written immediately to the
output. The events for the next timestamp are delayed proportionally to the
difference of the timestamps, and so on. The acceleration (the inverse of the
proportionality factor) is set with option -a.

By default, the output is written to standard output. If option -o together with
a hostname and port is given, a TCP server listening to that address is created.
The first client connecting to the TCP server will receive the event stream. The
first event is only sent once a client has connected. No more clients will be
accepted afterwards.

The replayer maintains some statistics about the number of events processed. It
also keeps track of the delay between the scheduled event time and the time the
event was eventually written to the output. The latter may be useful if another
process is reading the event stream via a pipe or socket. Use options -v or -vv
to retrieve a report once every second, which is written to standard error. The
reports contain the current, peak, and maximum delay. The current delay is the
delay of the most recent event. The peak delay is the highest delay that has
been observed between the previous and the current measurement. The maximum
delay is the highest delay that has been observed so far. Note that delays are
tracked only up to the operating system's buffer that is associated with the
pipe or socket. The current and peak delay are zero if no event could be issued
in the last second.

The implementation of the replayer uses two threads, one for reading and one
for writing events, which are connected by a queue with limited capacity. If
the queue is drained fully, an underrun occurs and events may not be reproduced
at the appropriate time. The verbose report (-vv) displays the number of
underruns. If this number is non-zero and especially if it is growing, the
queue capacity should be increased with option -q.

The compact report (-v) consists of one line of measurements per second. Each
line consists of the following fields, which are separated by spaces:

  - elapsed time,
  - the current index rate (1/s),
  - the current event rate (1/s),
  - the current delay at the time of the measurement,
  - the peak delay observed between the previous and current measurements,
  - the maximum delay observed so far, and
  - the average delay.

All values are printed in seconds unless noted otherwise.
