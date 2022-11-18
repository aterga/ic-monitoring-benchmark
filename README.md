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

The archive `fm23-ic-monitoring.tar` contains a ready-to-use docker image with
all programs and scripts. See the section on replication below for instructions.

The docker image does not embed the raw log files because of their size.
They are provided separately in the `data` folder, specifically:

- `data/mainnet.raw.log`: The three hour fragment of the production log.
- `data/system-tests/*.raw.log`: Logs from three runs of every hourly and
  nightly system test. The file names have a numeric suffix that distinguishes
  the different runs; the rest of the name identifies the system test.

Each raw log file contains a single JSON array with the events. The events were
obtained in this format from the IC's ElasticSearch server.

To simplify the inspection of the artifact, as well as to facilitate reuse and
extension, we provide the sources used to build the docker image in the `src`
folder. Specifically, the image can be rebuilt by running `docker build -t
fm23-ic-monitoring` in that folder. The sources are **not** required to perform
the replication. Therefore, we give a high-level summary of their structure:

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

- `experiments/`: Scripts that prepare, execute, and summary our performance
  experiments.
  - `simulate_online.sh`: Combines MonPoly and the real-time stream simulator
    (a.k.a. replayer). Additionally, a text file reporting the replayer latency
    at every second is produced.
  - `index_rate.mfotl`: MFOTL formula used to compute the index rate of the
    production log.
  - `make_table.py`: Reads the statistics collected by the monitoring pipeline
    and aggregates them to produce Table 2 from the paper.
  - `make_plot.py`: Reads the replayer latency reports and produces Figure 5.
  - `requirements.txt`: Python dependencies, including those of the monitoring
    pipeline.

- `Dockerfile` and `.dockerignore`: For building the docker image.


Policy formulas
---------------

We provide formalizations of all policies described in the paper. There is
a folder in `src/policy-monitoring/mfotl-policies` for every policy. (The
`policy-monitoring` subtree also exists in the docker image below `/work`.)
Each folder contains an MFOTL formula, expressed using MonPoly's concrete
syntax, as well as satisfying and violating example logs for testsing. The MFOTL
include comments.

TODO: name differences?

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
- ?GB of RAM
- ?GB of free disk space, excluding the supplied files

The authors performed the experiments on a server with two 3 GHz 16-core
AMD EPYC 7302 CPUs, a SSD, and 512 GiB RAM, running under Linux 5.4.0.

All command lines (indicated by the leading '$' sign, which is not part of the
command) in the following instructions must be executed in a bash shell, whose
current working directory contains at least the tar file and the entire `data`
folder. The latter must be writable.

### Preparation

Run the commands

    $ docker load -i fm23-ic-monitoring.tar
    $ docker run -v `pwd`/data:/work/data localhost/fm23-ic-monitoring prepare

the import the docker image 
in this order to compile all programs that are part of the artifact and generate
the traces for the experiments. Compiler warnings during the build are expected.
The trace generation may take several minutes. The traces can be found in the
newly created `traces` folder.

### Running the experiments

TODO:

WARNING: The experiments are expected to take ??? hours.

### Post-processing

TODO
