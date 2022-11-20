Monitoring the Internet Computer (Evaluation Artifact)
======================================================



Synopsis
--------

This artifact accompanies the paper "Monitoring the Internet Computer", which
will be presented at the 25th International Symposium on Formal Methods
(FM 2023). It provides the policy formulas described in Section 3.2 of the
paper, the raw log files that were used in the evaluation, and all tools
necessary to replicate the experimental results, specifically those reported in
Table 2 and Figure 5 in the paper.

This file contains three parts: 
1. overview of the artifact files
2. instructions for setting up the experiments
3. instructions for replicating the experiments


Overview of the Artifact Files
------------------------------

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

### Summary of the remaining files:

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

- `test-inputs`: Log files to quickly test the experiment scripts, as explained
  below.

- `Dockerfile`: For building the docker image.


### Policy formulas

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


Setting up the Experiments
--------------------------

Our artifact is shipped as a Docker image; we therefore assume that the user has Docker installed on their host system.

We tested the artifact on a system running Linux 5.4.0 (Docker version 20.10.21, build `baeda1f`), but other OS supporting Docker should also work.

### System requirements

- **CPU:** We tested the artifact on a server with two 3 GHz 16-core AMD EPYC 7302 CPUs. 
- **RAM:** 180 GiB for running the full set of experiments. **Note** 8 GiB of RAM is sufficient for running a reduced set of representative experiments.
- **Disk space:** At least ca. 80 GB of free disk space (ca. 60 GB is the size of the artifact itself)
- **Time:** TODO

| Group                       | Time (full experiments) | Time (reduced set)
| ----------------------------|-------------------------|--------------------
| offline system tests        | 6h (???)                | 30m
| offline production          | 11h so far              | 2h
| online production (prepare) | 2 hours                 | 2h
| online production (monitor) | 8 hours                 | 3h

### Preparation

1. Download the artifact archive from Zenodo (TODO: link)
2. Unpack archive (TODO)

Ensure that you have a copy of all artifact files on your local disk. The command
lines in the following instructions should be executed in a Bash shell, whose
current working directory contains the artifact files. It must be writable, as
the output of various programs is stored within the `data` subfolder. If you do
not use bash, you might need to adjust the commands.

Run the command

    docker load -i ic-monitoring-benchmark.tar

to import the docker image. Then execute

    docker run -itv `pwd`:/work ic-monitoring-benchmark

to start the container, mounting the current working directory. You are now in
a bash session running in the container. **All commands in the following
sections must be issued in this session**. You can leave the container with
`exit`.


Setup validation
----------------

Please follow steps A-C below to validate that the artifact is set up correctly.

**Note:** the instructions in this section are intended for validating that this
artifact is _functional_. The validation steps will take a short time to
execute, but the data produced by running these validation steps do not
represent our paper's results. For replicating the actual paper experiments,
please follow the instructions _after_ this section.

----

### A. Validating the **online** monitoring benchmark

Run the following commands to validate the online monitoring experiment (for our
simplest policy, `clean_logs`) based on (a small prefix of) the production logs.

    rm -fr data/online/
    ./experiments/entrypoints/prepare.sh -l ./test-inputs/production/mainnet-3h-filtered-top100.raw.log
    ./experiments/entrypoints/online-monitoring.sh clean_logs

The expected output graphic in `data/online/latency.png` should look like this:

![Sample latency graph for the online monitoring experiment](./docs/latency.png "Sample latency graph for the online monitoring experiment")

----

### B. Validating the offline monitoring benchmark (based on **system test logs**)

Run the following commands to validate the offline monitoring experiment based
on (a small subset of) the system test logs. 

    rm -fr data/offline/
    ./experiments/entrypoints/offline-monitoring-system-tests.sh -l ./test-inputs/system-tests
    cat data/offline/results.txt

Expected outcome:

```
---------------------------------------------------------------------------------
Measurement               | Testing                   | Prod                     
---------------------------------------------------------------------------------
Raw log entries           |     1900    (   13851   ) |      nan    (     nan   )
Raw log size              |        2.8  (      26.3 ) |        nan  (       nan )
Processed events          |       19    (    1394   ) |      nan    (     nan   )
Processed events/s        |       10.2  (      28.7 ) |        nan  (       nan )
Processed log size        |        0.0  (       0.5 ) |        nan  (       nan )
Preprocessor time         |        0.07 (       0.11) |         nan (        nan)
---------------------------------------------------------------------------------
clean_logs                | 18.28 (  26.6)    9 (  10) |  nan (   nan)  nan ( nan)
logging_behavior__exe     | 18.03 (  24.3)   10 (  12) |  nan (   nan)  nan ( nan)
unauthorized_connections  | 18.17 (  25.6)    9 (  11) |  nan (   nan)  nan ( nan)
reboot_count              | 18.16 (  24.9)    9 (  10) |  nan (   nan)  nan ( nan)
finalization_consistency  | 17.48 (  24.7)    9 (  10) |  nan (   nan)  nan ( nan)
finalized_height          | 17.74 (  24.8)   10 (  10) |  nan (   nan)  nan ( nan)
replica_divergence        | 17.60 (  24.4)    9 (  10) |  nan (   nan)  nan ( nan)
block_validation_latency  | 17.47 (  25.3)   10 (  15) |  nan (   nan)  nan ( nan)
---------------------------------------------------------------------------------
```

The `nan` values above are _expected_ (since only a small subset of tests were 
invoked for validating the artifact setup). The measured times and memory usages
might be slightly different due to variations in the environment.

----

### C. Validating the offline monitoring benchmark (based on **production logs**)

Run the following commands to validate the offline monitoring experiment (for
our simplest policy, `clean_logs`) based on (a small prefix of) the production
logs. 

    rm -fr data/offline/
    ./experiments/entrypoints/offline-monitoring-production.sh -l ./test-inputs/production/mainnet-3h-filtered-top100.raw.log
    cat data/offline/results.txt

Expected outcome:

```
---------------------------------------------------------------------------------
Measurement               | Testing                   | Prod                     
---------------------------------------------------------------------------------
Raw log entries           |      nan    (     nan   ) |      100    (     100   )
Raw log size              |        nan  (       nan ) |        0.4  (       0.4 )
Processed events          |      nan    (     nan   ) |     1323    (    1323   )
Processed events/s        |        nan  (       nan ) |    52920.0  (   52920.0 )
Processed log size        |        nan  (       nan ) |        0.2  (       0.2 )
Preprocessor time         |         nan (        nan) |        4.30 (       4.30)
---------------------------------------------------------------------------------
clean_logs                |  nan (   nan)  nan ( nan) | 0.36 (   0.4)   11 (  11)
---------------------------------------------------------------------------------
```

Again, the `nan` values in the above table are _expected_ and the numbers might
be slightly different.

----


Replicating the experiments
---------------------------

WARNING: Running all experiments will take roughly ??? hours as the experiments
are not parallelized. We recommend a faster subset in the next section. You
should read this section nonetheless as it explains the individual scripts.

Before proceeding, any output produced by the setup validation steps should be
deleted using

    rm -fr data/offline/
    rm -fr data/online/

There are three groups of experiments: offline monitoring of the system tests,
offline monitoring of the production log, and online monitoring of the
production log. The offline monitoring experiments can be started with the
commands

    ./experiments/entrypoints/offline-monitoring-system-tests.sh
    ./experiments/entrypoints/offline-monitoring-production.sh

respectively. For online monitoring, it is first necessary to run

    ./experiments/entrypoints/prepare.sh

which performs some mandatory preparatory steps (converting and analyzing the
production log in advance for the online experiments). This takes approximately
2 hours. The experiment itself is started with

    ./experiments/entrypoints/online-monitoring.sh

Once the scripts have concluded, the results corresponding to Table 2 can be
found in `data/offline/results.txt`. The units are the same as in the paper. The
plot for Figure 5 is always exported to `data/online/latency.png`.

Note that these files are also accessible from outside of the container, thanks
to the mounted volume.


Monitoring a subset of policies
-------------------------------

It is possible to customize the policies being monitored by providing them as
arguments to the above scripts invocations. Unlike in the "setup validation"
instructions, this results in measurements that are comparable to those obtained
from the full benchmark; the only difference is that some measurements are
missing.

We recommend the following:

    rm -fr data/offline/  # clean up
    ./experiments/entrypoints/offline-monitoring-system-tests.sh clean_logs reboot_count
    ./experiments/entrypoints/offline-monitoring-production.sh clean_logs reboot_count

    rm -fr data/online/   # clean up
    ./experiments/entrypoints/prepare.sh
    ./experiments/entrypoints/online-monitoring.sh clean_logs

These only monitor the `clean_logs` (offline and online) and `reboot_count`
(offline) policies. It is again possible to run only a subset of the three
experiment groups. The expected running times are

| Group                       | Time (full experiments) | Time (reduced set)
| ----------------------------|-------------------------|--------------------
| offline system tests        | 6h (???)                | 30m
| offline production          | 11h so far              | 2h
| online production (prepare) | 2 hours                 | 2h
| online production (monitor) | 8 hours                 | 3h
