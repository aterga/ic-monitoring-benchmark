# How to build the artifact

1. clone repo and `cd` into it
2. `git submodule update --init`

3. `docker build -t ic-monitoring-benchmark .`
4. `docker save -o ic-monitoring-benchmark.tar ic-monitoring-benchmark`

5. `mkdir data`
6. copy `production.raw.log` into data
7. copy `system-tests` folder into data
8. `cp test-inputs/production/mercury-reg-snap--20220905_212707.json data/`

9. `rm -rf .git .gitignore .gitmodules monpoly/.git building-artifact.md`
10. zip -r ic-monitoring-benchmark.zip .`
11. `sha256sum ic-monitoring-benchmark.zip`
