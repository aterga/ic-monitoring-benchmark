Monitoring the Internet Computer
================================

Synopsis
--------

This artifact accompanies the paper "Monitoring the Internet Computer", which
will be presented at the 25th International Symposium on Formal Methods
(FM 2023). It provides the policy formulas described in Section 3.2 of the
paper, the raw log files that were used in the evaluation, and all tools
necessary to replicate the experimental results, specifically those reported in
Table 2 and Figure 5 in the paper.


Files
-----

The archive `ic-monitoring-benchmark.tar` contains a ready-to-use docker image
with all tools and dependencies preinstalled. See the section on replication
below for instructions.

The docker image does not embed the raw log files because of their size.
They are provided separately in the `data` folder, specifically:

- `data/production.raw.log`: The three hour fragment of the production log.
- `data/mercury-reg-snap--20220905_212707.json`: Snapshot of the IC's registry
  state at the begin of the production log.
- `data/system-tests/*.raw.log`: Logs from three runs of every hourly and
  nightly system test. The file names have a numeric suffix that distinguishes
  the different runs; the rest of the name identifies the system test.

Each raw log file contains a single array (in Python format) with the log entries
 from IC nodes.

By following the instructions below, the folder that contains this readme file
is mounted as a volume within the docker container. All scripts in the
`policy-monitoring` and `experiment` subfolders are directly executed from this
volume and can thus be edited without rebuilding the docker image. In addition,
we provide the sources that were used to build the image to simplify the
inspection of the artifact, as well as to facilitate reuse and extensions. If
desired, the image can be rebuilt by running `docker build -t
ic-monitoring-benchmark`.

Summary of the remaining files:

- `monpoly/`: Source code (OCaml) of the MonPoly monitoring tool. This is a copy
  of commit f2825be8fa1a0684dd0eb1da8c6f5cd87724d0f2 from
  <https://bitbucket.org/jshs/monpoly.git>.

- `replayer/`: Source code (Java/Scala) of the real-time log stream simulator
  used in the online monitoring experiments. This is a copy of commit
  da7a2e6fee3d68adec70e864ba0152092a94fad9 from
  <https://bitbucket.org/krle/scalable-online-monitor.git>. The main program can
  be found in `replayer/src/main/java/ch/ethz/infsec/replayer/Replayer.java`.

- `policy-monitoring/`: The monitoring pipeline that we developed for the IC.
  - `mfotl-policies/`: The formalized policies, see below for details.
  - `monpoly/`: Python wrapper around MonPoly.
  - `pipeline/`: Contains the log preprocessor.
  - `main.py`: Entry point for running the pipeline.

- `experiments/`: Scripts that prepare, execute, and summarize our performance
  experiments.
  - `entrypoints/*.sh`: Driver scripts, see below.
  - `simulate_online.sh`: Combines MonPoly and the real-time stream simulator
    (a.k.a. replayer). Additionally, a text file reporting the replayer latency
    at every second is produced.
  - `index_rate.mfotl`: MFOTL formula used to compute the index rate of the
    production log.
  - `make_online.py`: Adjusts the time-stamps of the initial registry state in
    the preprocessed production log to be shortly before the first proper event.
    Without this change, the replayer would introduce an excessive delay.
  - `make_table.py`: Reads the statistics collected by the monitoring pipeline
    and aggregates them to produce Table 2 from the paper.
  - `make_plot.py`: Reads the replayer latency reports and produces Figure 5.
  - `requirements.txt`: Python dependencies, including those of the monitoring
    pipeline.

- `Dockerfile`: For building the docker image.


Policy formulas
---------------

We provide formalizations of all policies described in the paper. There is
a folder in `src/policy-monitoring/mfotl-policies` for every policy. (The
`policy-monitoring` subtree also exists in the docker image below `/work`.)
Each folder contains an MFOTL formula, expressed using MonPoly's concrete
syntax, as well as satisfying and violating example logs for testsing. The MFOTL
include comments.

Most aspects of the concrete formula syntax are described in the paper *The
MonPoly Monitoring Tool* (David Basin, Felix Klaedtke, Eugen Zalinescu. RV-CuBES
2017: 19-28 <https://doi.org/10.29007/89hs>). Let operators are a more recent
feature, which were introduced to MFOTL by the paper *Verified First-Order
Monitoring with Recursive Rules* (Sheila Zingg, Srdan Krstic, Martin Raszyk,
Joshua Schneider, Dmitriy Traytel. TACAS 2022: 236-253
<https://doi.org/10.1007/978-3-030-99527-0_13>).


Replication
-----------

# Prerequisites

Minimal requirements:

- Docker
- ???GB of RAM
- ???GB of free disk space, excluding the supplied files

The authors originally performed the experiments on a server with two 3 GHz
16-core AMD EPYC 7302 CPUs, a SSD, and 512 GiB RAM, running under Linux 5.4.0.

### Preparation

Ensure that you have a copy of all artifact files on a local disk. The command
lines (indicated by the leading '$' sign, which is not part of the command) in
the following instructions should be executed in a bash shell, whose current
working directory contains the artifact files. It must be writable, as the
output of various programs is stored within the `data` subfolder. If you do not
use bash, you might need to adjust the commands.

Run the command

    $ docker load -i ic-monitoring-benchmark.tar

to import the docker image. Then execute

    $ docker run -itv `pwd`:/work ic-monitoring-benchmark

to start the container, mounting the current working directory. You are now in
a bash session running in the container. The following commands must be issued
in this session; we prefix them with `#`. You can leave the container with
`exit`.

    # ./experiments/entrypoints/prepare.sh

performs some mandatory preparatory steps (converting and analyzing the
production log in advance for the online experiments). This takes approximately
??? minutes.

### Running the experiments

The full set of experiments can be performed with

    # ./experiments/entrypoints/full.sh

WARNING: This will take roughly ??? hours as the experiments are not
parallelized. Therefore, we provide a significantly reduced but much faster set:

    # ./experiments/entrypoints/reduced-offline.sh
    # ./experiments/entrypoints/reduced-online.sh

These only monitor the `clean_logs` (offline and online) and `reboot_count`
(offline) policies.

Moreover, it is possible to customize the policies being monitored by invoking
the experiments directly as follows.

    # ./experiments/entrypoints/offline-monitoring-system-tests.sh POLICIES...
    # ./experiments/entrypoints/offline-monitoring-production.sh POLICIES...
    # ./experiments/entrypoints/online-monitoring.sh POLICIES...

In this case, it is necessary to trigger the summarization manually:

    # python3 ./experiments/make_table.py > ./data/offline/results-custom.txt
    # python3 ./experiments/make_plot.py

### Results

Once the scripts have concluded, the results corresponding to Table 2 can be
found in `data/offline/results.txt` (or `data/offline/results-reduced.txt` if
the reduced set was used). The units are the same as in the paper. The plot for
Figure 5 is exported to `data/online/latency.png`.

Note that these files are also accessible from outside of the container, thanks
to the mounted volume.
