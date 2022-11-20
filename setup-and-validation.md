# Setup validation

**Note:** the instructions in this section are intended for validating that this artifact is _functional_. The validation steps will take a short time to execute, but the data produced by running these validation steps do not represent our paper's results. For replicating the actual paper experiments, please follow the instructions _after_ this section.

----

### Validating the **online** monitoring benchmark

Run the following commands to validate the online monitoring experiment (for our simplest policy, `clean_logs`) based on (a small prefix of) the production logs.

```
rm -fr data/online/
./experiments/entrypoints/prepare.sh -l ./test-inputs/production/mainnet-3h-filtered-top100.raw.log
./experiments/entrypoints/online-monitoring.sh clean_logs
```

The expected output graphic in `data/online/latency.png` should look like this:

![Sample latency graph for the online monitoring experiment](./docs/latency.png "Sample latency graph for the online monitoring experiment")

----

### Validating the offline monitoring benchmark (based on **system test logs**)

Run the following commands to validate the offline monitoring experiment based on (a small subset of) the system test logs. 

```bash
./experiments/entrypoints/offline-monitoring-system-tests.sh -l ./test-inputs/system-tests
cat data/offline/results.txt
```

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
invoked for validating the artifact setup). 

----

### Validating the offline monitoring benchmark (based on **production logs**)

Run the following commands to validate the offline monitoring experiment (for our simplest policy, `clean_logs`) based on (a small prefix of) the production logs. 

```bash
rm -fr data/online/
./experiments/entrypoints/offline-monitoring-production.sh -l ./test-inputs/production/mainnet-3h-filtered-top100.raw.log
cat data/offline/results.txt
```

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

Again, the `nan` values in the above table are _expected_.

----