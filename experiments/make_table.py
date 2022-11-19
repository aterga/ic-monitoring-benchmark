#!/usr/bin/env python

"""
Parses pipeline statistics from the offline monitoring experiments and prints a table with the
results.
"""

import glob
import logging
import os.path
import re
import sys
import yaml

import numpy as np
import pandas as pd

sys.path.append('policy-monitoring')
import util.yaml  # required for parsing custom data types in the yaml files


def load_stat_file(scenario, filename):
    """Extract the relevant measurements from a yaml file produced by the pipeline.

    The returned DataFrame has the following columns:
      - scenario: test or prod
      - pot: name of the system test, or 'mainnet*' for the production log
      - policy: name of the monitored policy
      - exit_code: of MonPoly
      - test_runtime: duration of the system test execution (seconds)
      - num_events: number of events in the preprocessed log
      - num_violations: number of violations (lines) reported by MonPoly
      - monpoly_mem: maximum resident set size of the MonPoly process (MiB)
      - process_time: elapsed real time spent in the pipeline's wrapper around MonPoly (seconds)
      - preprocessor_time: elapsed real time spent preprocessing
      - nodes: initial number of IC nodes
      - subnets: initial number of IC subnets
    """
    logging.info("Loading %s", filename)
    with open(filename, 'r') as f:
        raw = yaml.full_load(stream=f)
    data = []
    for pot, potdata in raw.items():
        preproc = potdata['pre_processor']
        global_infra = potdata['global_infra']
        nodes = len(global_infra['original_nodes'])
        subnets = len(global_infra['original_subnet_types'])
        mdata = potdata['monpoly']
        if len(mdata) > 1:
            logging.warning("%s: Preprocessing shared by %d policies in pot %s)",
                    filename, len(mdata), pot)
        for policy, poldata in mdata.items():
            if 'perf_metrics' in poldata:
                perf = poldata['perf_metrics']
                row = [
                        scenario,
                        pot.removesuffix('--pseudo'),
                        policy,
                        int(poldata['exit_code']),
                        float(preproc['test_runtime_milliseconds']) / 1000,
                        int(poldata['num_events']),
                        int(poldata['num_violations_reported']),
                        float(perf['maximum_resident_set_size_kbytes']) / 1024,
                        float(poldata['process_duration_seconds']),
                        float(preproc['pre_processing']['perf_counter_seconds']),
                        nodes,
                        subnets
                        ]
                data.append(row)
            else:
                logging.warning("%s: No metrics for policy %s in pot %s. Timeout?", filename, policy, pot)
    return pd.DataFrame(data, columns=['scenario', 'pot', 'policy', 'exit_code', 'test_runtime',
                                       'num_events', 'num_violations',
                                       'monpoly_mem', 'process_time', 'preprocessor_time',
                                       'nodes', 'subnets'])

def load_raw_counts(filename):
    """Load the number of raw log entries in each log file from the given csv file.

    The returned DataFrame has the following columns:
      - pot: name of the system test, or 'mainnet*' for the production log
      - raw_entries: number of raw log entries
    """
    logging.info("Loading %s", filename)
    data = []
    with open(filename, 'r') as f:
        for l in f:
            m = re.match(r'^[^:]*?([^:/]+)\.log,(\d+)$', l)
            if m:
                data.append([m.group(1), int(m.group(2))])
            else:
                logging.warning("%s: Skipping line \"%s\"", filename, l)
    return pd.DataFrame(data, columns=['pot', 'raw_entries'])

def load(basedir):
    """Load all measurements from the offline monitoring experiments."""
    logging.info("Searching for pipeline stats in %s", basedir)
    ds = []
    for filename in glob.glob(basedir + '/offline/*/*/stat.yaml'):
        m = re.match(r'.*/(production|system-tests)/[^/]+/stat\.yaml$', filename)
        if m:
            ds.append(load_stat_file(m.group(1), filename))

    raw_counts = load_raw_counts(basedir + '/raw_counts.txt')
    logging.info("Loaded all files")

    # Combine pipeline stats and raw event counts
    data = pd.concat(ds)
    data.reset_index(drop=True, inplace=True)
    raw_counts.set_index('pot', inplace=True)
    data = data.join(raw_counts, on='pot', how='left')

    # Compute derived statistics
    data['monpoly_ntime'] = data['process_time'] / data['num_events'] * 1000  # milliseconds
    data['preproc_ntime'] = data['preprocessor_time'] / data['raw_entries'] * 1000  # milliseconds
    data['event_rate'] = data['num_events'] / data['test_runtime']
    return data

def print_table(data):
    """Pretty-print result table to stdout."""

    TABLE_HEADER = '{:25} | {:25} | {:25}'
    TABLE_ROW1 = '{:25} | {:>11} ({:>11}) | {:>11} ({:>11})'
    TABLE_ROW2 = '{:25} | {:>4} ({:>6}) {:>4} ({:>4}) | {:>4} ({:>6}) {:>4} ({:>4})'
    TABLE_SEP = 81 * '-'

    # Log statistics and preprocessor time
    logs = data[data['policy'] == 'clean_logs'].groupby('scenario').agg(
            {
                'raw_entries': [np.median, np.max],
                'num_events': [np.median, np.max],
                'event_rate': [np.median, np.max]
            })
    preproc = data.groupby('scenario').agg({'preproc_ntime': [np.median, np.max]})

    common = pd.concat([logs, preproc], axis=1).stack(level=0).unstack(level='scenario')
    common.columns = common.columns.reorder_levels([1, 0])
    common.sort_index(axis=1, ascending=False, inplace=True)

    print(TABLE_SEP)
    print(TABLE_HEADER.format('Measurement', 'Testing', 'Prod'))
    print(TABLE_SEP)
    print(TABLE_ROW1.format('Raw log entries',
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('system-tests', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('system-tests', 'amax'), np.nan)),
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('production', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Processed events',
                            '{:.0f}   '.format(common.loc['num_events'].get(('system-tests', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['num_events'].get(('system-tests', 'amax'), np.nan)),
                            '{:.0f}   '.format(common.loc['num_events'].get(('production', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['num_events'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Processed events/s',
                            '{:.1f} '.format(common.loc['event_rate'].get(('system-tests', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['event_rate'].get(('system-tests', 'amax'), np.nan)),
                            '{:.1f} '.format(common.loc['event_rate'].get(('production', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['event_rate'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Preprocessor time',
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('system-tests', 'median'), np.nan)),
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('system-tests', 'amax'), np.nan)),
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('production', 'median'), np.nan)),
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('production', 'amax'), np.nan))))
    print(TABLE_SEP)

    # Performance per policy
    perf = data.groupby(['scenario', 'policy']).agg({'monpoly_ntime': [np.median, np.max],
                                                    'monpoly_mem': [np.median, np.max]}).unstack(0)
    perf.columns = perf.columns.reorder_levels([2, 0, 1])
    perf.sort_index(axis=1, ascending=False, inplace=True)

    POLICIES = [
            'clean_logs',
            'logging_behavior__exe',
            'unauthorized_connections',
            'reboot_count',
            'finalization_consistency',
            'finalized_height',
            'replica_divergence',
            'block_validation_latency'
            ]

    for p in POLICIES:
        if p not in perf.index:
            continue
        r = perf.loc[p]
        print(TABLE_ROW2.format(p,
                               '{:.2f}'.format(r.get(('system-tests', 'monpoly_ntime', 'median'), np.nan)),
                               '{:.1f}'.format(r.get(('system-tests', 'monpoly_ntime', 'amax'), np.nan)),
                               '{:.0f}'.format(r.get(('system-tests', 'monpoly_mem', 'median'), np.nan)),
                               '{:.0f}'.format(r.get(('system-tests', 'monpoly_mem', 'amax'), np.nan)),
                               '{:.2f}'.format(r.get(('production', 'monpoly_ntime', 'median'), np.nan)),
                               '{:.1f}'.format(r.get(('production', 'monpoly_ntime', 'amax'), np.nan)),
                               '{:.0f}'.format(r.get(('production', 'monpoly_mem', 'median'), np.nan)),
                               '{:.0f}'.format(r.get(('production', 'monpoly_mem', 'amax'), np.nan))))
    print(TABLE_SEP)

if __name__ == '__main__':
    data = load('data')
    print_table(data)
