### Setup validation


```bash
rm -fr data/online/
./experiments/entrypoints/prepare.sh -l ./test-inputs/production/mainnet-3h-filtered-top100.raw.log
./experiments/entrypoints/online-monitoring.sh clean_logs
rm -fr data/offline/
./experiments/entrypoints/offline-monitoring-system-tests.sh -l ./test-inputs/system-tests
./experiments/entrypoints/offline-monitoring-production.sh -l ./test-inputs/production/mainnet-3h-filtered-top100.raw.log
```

```
root@a0e3a48db044:/work# cat data/offline/results.txt 
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

The `nan` values above are expected (since only a small subset of tests were 
invoked for validating the artifact setup). 